package com.umis.event;

import com.umis.email.model.EmailMessage;
import com.umis.email.repository.EmailMessageRepository;
import com.umis.event.consumer.dto.CreateConsumerRequest;
import com.umis.event.consumer.dto.ProcessingAcknowledgementRequest;
import com.umis.event.consumer.dto.ProcessingStatusDto;
import com.umis.event.consumer.model.ProcessingStatus;
import com.umis.event.consumer.service.ConsumerService;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.SyncStrategy;
import com.umis.mailbox.repository.MailboxConnectionRepository;
import com.umis.security.RoleConstants;
import com.umis.security.TenantContext;
import com.umis.security.UserPrincipal;
import com.umis.tenant.model.Tenant;
import com.umis.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ConsumerAcknowledgementTest {

    @Autowired
    private ConsumerService consumerService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private MailboxConnectionRepository mailboxRepository;

    @Autowired
    private EmailMessageRepository emailRepository;

    private Tenant tenant;
    private EmailMessage email;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(new Tenant("tenant-ack-" + UUID.randomUUID(), "Ack Corp"));
        setSecurityContext(UUID.randomUUID(), tenant.getId(), RoleConstants.TENANT_ADMIN);

        MailboxConnection mailbox = mailboxRepository.save(new MailboxConnection(
                tenant.getId(), "orders@ack.com", "Orders", MailboxProvider.MOCK, SyncStrategy.POLLING));

        email = emailRepository.save(new EmailMessage(
                tenant.getId(), mailbox, MailboxProvider.MOCK, "prov-msg-999",
                "customer@buyer.com", "Buyer Co", "Order #1234", "Please process", "hash-ack-1", Instant.now()));

        // Register two downstream consumer applications
        consumerService.registerConsumer(new CreateConsumerRequest(
                tenant.getId(), "PO_PROCESSING", "Purchase Order Extraction Service", "Extracts POs", "EMAIL_INGESTED"));
        consumerService.registerConsumer(new CreateConsumerRequest(
                tenant.getId(), "CRM", "Customer Relationship Management", "Links emails to leads", "EMAIL_INGESTED"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Multiple consumers maintain independent processing statuses on the same email")
    void testMultipleConsumersIndependentStatus() {
        // Consumer 1: PO_PROCESSING reports SUCCESS
        ProcessingStatusDto poAck = consumerService.acknowledgeProcessing(
                email.getId(),
                new ProcessingAcknowledgementRequest(
                        "PO_PROCESSING", ProcessingStatus.SUCCESS, Instant.now(), "po-ref-77", "Extracted", "idemp-1")
        );
        assertEquals(ProcessingStatus.SUCCESS, poAck.status());
        assertEquals("po-ref-77", poAck.referenceId());

        // Consumer 2: CRM reports REVIEW_REQUIRED
        ProcessingStatusDto crmAck = consumerService.acknowledgeProcessing(
                email.getId(),
                new ProcessingAcknowledgementRequest(
                        "CRM", ProcessingStatus.REVIEW_REQUIRED, Instant.now(), "lead-404", "Manual lead matching required", "idemp-2")
        );
        assertEquals(ProcessingStatus.REVIEW_REQUIRED, crmAck.status());
        assertEquals("lead-404", crmAck.referenceId());

        // Verify independent statuses when querying email
        List<ProcessingStatusDto> statuses = consumerService.getProcessingStatuses(email.getId());
        assertEquals(2, statuses.size());

        ProcessingStatusDto retrievedPo = statuses.stream()
                .filter(s -> s.consumerCode().equals("PO_PROCESSING")).findFirst().orElse(null);
        assertNotNull(retrievedPo);
        assertEquals(ProcessingStatus.SUCCESS, retrievedPo.status());

        ProcessingStatusDto retrievedCrm = statuses.stream()
                .filter(s -> s.consumerCode().equals("CRM")).findFirst().orElse(null);
        assertNotNull(retrievedCrm);
        assertEquals(ProcessingStatus.REVIEW_REQUIRED, retrievedCrm.status());
    }

    private void setSecurityContext(UUID userId, UUID tenantId, String role) {
        UserPrincipal principal = new UserPrincipal(
                userId, tenantId, "test@" + role.toLowerCase() + ".com", "pass", role, true);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
        } else {
            TenantContext.clear();
        }
    }
}
