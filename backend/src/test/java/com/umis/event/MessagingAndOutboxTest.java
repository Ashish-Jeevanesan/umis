package com.umis.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umis.email.model.EmailMessage;
import com.umis.email.model.IngestionStatus;
import com.umis.email.repository.EmailMessageRepository;
import com.umis.email.service.EmailIngestionService;
import com.umis.event.consumer.dto.ProcessingAcknowledgementRequest;
import com.umis.event.consumer.dto.ProcessingStatusDto;
import com.umis.event.consumer.model.EmailProcessingStatus;
import com.umis.event.consumer.model.ProcessingStatus;
import com.umis.event.consumer.repository.EmailProcessingStatusRepository;
import com.umis.event.consumer.service.ConsumerService;
import com.umis.event.model.EventOutbox;
import com.umis.event.model.OutboxStatus;
import com.umis.event.publisher.EventPublisher;
import com.umis.event.publisher.OutboxRelayScheduler;
import com.umis.event.repository.EventOutboxRepository;
import com.umis.mailbox.connector.model.NormalizedEmail;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.SyncStrategy;
import com.umis.mailbox.repository.MailboxConnectionRepository;
import com.umis.security.RoleConstants;
import com.umis.security.TenantContext;
import com.umis.security.UserPrincipal;
import com.umis.tenant.model.Tenant;
import com.umis.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessagingAndOutboxTest {

    @Autowired
    private EmailIngestionService ingestionService;

    @Autowired
    private ConsumerService consumerService;

    @Autowired
    private EventOutboxRepository outboxRepository;

    @Autowired
    private EmailMessageRepository messageRepository;

    @Autowired
    private EmailProcessingStatusRepository statusRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private MailboxConnectionRepository mailboxRepository;

    @Autowired
    private OutboxRelayScheduler relayScheduler;

    @MockBean
    private EventPublisher eventPublisher;

    private Tenant tenant;
    private MailboxConnection mailbox;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(new Tenant("ORG-MSG", "Messaging Org"));
        mailbox = mailboxRepository.save(new MailboxConnection(
                tenant.getId(), "inbox@org-msg.com", "Msg Mailbox",
                MailboxProvider.MICROSOFT_365, SyncStrategy.POLLING));

        TenantContext.setTenantId(tenant.getId());
        String roleName = RoleConstants.TENANT_ADMIN.startsWith("ROLE_")
                ? RoleConstants.TENANT_ADMIN.substring(5)
                : RoleConstants.TENANT_ADMIN;
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), tenant.getId(), "admin@org-msg.com", "pass", roleName, true);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + roleName)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Transactional Outbox: Ingesting an email atomically persists both message and outbox event")
    void testAtomicEmailAndOutboxPersistence() {
        NormalizedEmail email = NormalizedEmail.create(
                tenant.getId(), mailbox.getId(), MailboxProvider.MICROSOFT_365,
                "msg_atomic_01", "vendor@corp.com", "Vendor", List.of("inbox@org-msg.com"),
                "Invoice INV-9001", "Payment details", "<p>Payment details</p>",
                Instant.now(), List.of(), "hash_atomic_01");

        EmailIngestionService.IngestionResult result = ingestionService.processNormalizedEmail(mailbox, email);
        assertNotNull(result.email().getId());

        List<EventOutbox> alOutbox = outboxRepository.findByStatusOrderByOccurredAtAsc(
                OutboxStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, 10));
        boolean hasEmailOutbox = alOutbox.stream()
                .anyMatch(event -> "EMAIL_INGESTED".equals(event.getEventType())
                        && event.getAggregateId().equals(result.email().getId().toString()));
        assertTrue(hasEmailOutbox, "Outbox event must be created atomically with email message");
    }

    @Test
    @DisplayName("Outbox Relay retries on RabbitMQ delivery failure without losing event")
    void testOutboxRelayRetriesOnRabbitMqFailure() {
        EventOutbox event = new EventOutbox(
                UUID.randomUUID(), "EMAIL_INGESTED", "EMAIL", "msg_test_id", tenant.getId(), "{}");
        outboxRepository.save(event);

        // Simulate RabbitMQ broker connection failure
        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(eventPublisher).publish(anyString(), anyString());

        relayScheduler.relayPendingEvents();

        EventOutbox updated = outboxRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxStatus.PENDING, updated.getStatus());
        assertEquals(1, updated.getRetryCount());
        assertTrue(updated.getLastError().contains("RabbitMQ connection refused"));
    }

    @Test
    @DisplayName("Consumer acknowledgements are idempotent when using the same idempotency key")
    void testConsumerAcknowledgementIdempotency() {
        EmailMessage message = messageRepository.save(new EmailMessage(
                tenant.getId(), mailbox, MailboxProvider.MICROSOFT_365, "msg_ack_01",
                "supplier@acme.com", "Acme Supplier", "Purchase Order #8871",
                "PO snippet", "hash_ack_01", Instant.now()));

        ProcessingAcknowledgementRequest request = new ProcessingAcknowledgementRequest(
                "PO_PROCESSING", ProcessingStatus.SUCCESS, Instant.now(), "PO-8871-DOC",
                "Extracted 3 line items", "idempotency_token_999");

        ProcessingStatusDto firstResponse = consumerService.acknowledgeProcessing(message.getId(), request);
        assertEquals(ProcessingStatus.SUCCESS, firstResponse.status());

        // Second submission with identical idempotency token
        ProcessingAcknowledgementRequest duplicateRequest = new ProcessingAcknowledgementRequest(
                "PO_PROCESSING", ProcessingStatus.FAILED, Instant.now(), "PO-8871-DOC",
                "This should not overwrite", "idempotency_token_999");

        ProcessingStatusDto secondResponse = consumerService.acknowledgeProcessing(message.getId(), duplicateRequest);

        // Status must remain SUCCESS and not be altered by the duplicate
        assertEquals(ProcessingStatus.SUCCESS, secondResponse.status());
        assertEquals("Extracted 3 line items", secondResponse.message());
    }

    @Test
    @DisplayName("Multiple downstream consumer modules track independent processing statuses on the same email")
    void testMultipleIndependentConsumerStatuses() {
        EmailMessage message = messageRepository.save(new EmailMessage(
                tenant.getId(), mailbox, MailboxProvider.MICROSOFT_365, "msg_multi_consumer",
                "client@globex.com", "Globex", "Inquiry & PO 500",
                "Snippet", "hash_multi_consumer", Instant.now()));

        // Consumer 1: PO_PROCESSING acknowledges SUCCESS
        consumerService.acknowledgeProcessing(message.getId(), new ProcessingAcknowledgementRequest(
                "PO_PROCESSING", ProcessingStatus.SUCCESS, Instant.now(), "PO-500", "PO matched", null));

        // Consumer 2: CRM_MODULE acknowledges FAILED
        consumerService.acknowledgeProcessing(message.getId(), new ProcessingAcknowledgementRequest(
                "CRM_MODULE", ProcessingStatus.FAILED, Instant.now(), "CRM-LEAD-0", "Customer not found", null));

        List<ProcessingStatusDto> alStatuses = consumerService.getProcessingStatuses(message.getId());
        assertEquals(2, alStatuses.size());

        boolean hasPoSuccess = alStatuses.stream().anyMatch(st ->
                "PO_PROCESSING".equals(st.consumerCode()) && st.status() == ProcessingStatus.SUCCESS);
        boolean hasCrmFailed = alStatuses.stream().anyMatch(st ->
                "CRM_MODULE".equals(st.consumerCode()) && st.status() == ProcessingStatus.FAILED);

        assertTrue(hasPoSuccess, "PO_PROCESSING status must be SUCCESS");
        assertTrue(hasCrmFailed, "CRM_MODULE status must be FAILED");
    }
}
