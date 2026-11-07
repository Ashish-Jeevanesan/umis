package com.umis.dashboard;

import com.umis.dashboard.dto.GlobalDashboardDto;
import com.umis.dashboard.dto.TenantDashboardDto;
import com.umis.dashboard.service.DashboardService;
import com.umis.email.model.EmailMessage;
import com.umis.email.model.IngestionJob;
import com.umis.email.model.IngestionStatus;
import com.umis.email.model.TriggerType;
import com.umis.email.repository.EmailMessageRepository;
import com.umis.email.repository.IngestionJobRepository;
import com.umis.event.consumer.dto.ProcessingAcknowledgementRequest;
import com.umis.event.consumer.model.ProcessingStatus;
import com.umis.event.consumer.service.ConsumerService;
import com.umis.event.model.EventOutbox;
import com.umis.event.model.OutboxStatus;
import com.umis.event.repository.EventOutboxRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardAccuracyTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private MailboxConnectionRepository mailboxRepository;

    @Autowired
    private EmailMessageRepository emailRepository;

    @Autowired
    private IngestionJobRepository jobRepository;

    @Autowired
    private EventOutboxRepository outboxRepository;

    @Autowired
    private ConsumerService consumerService;

    private Tenant tenant;
    private MailboxConnection mailbox;

    @BeforeEach
    void setUp() {
        tenant = tenantRepository.save(new Tenant("ORG-METRICS", "Metrics Org"));
        mailbox = mailboxRepository.save(new MailboxConnection(
                tenant.getId(), "metrics@org.com", "Metrics Mailbox",
                MailboxProvider.MICROSOFT_365, SyncStrategy.POLLING));

        authenticate(tenant.getId(), RoleConstants.GLOBAL_ADMIN);
    }

    private void authenticate(UUID tenantId, String strRole) {
        TenantContext.setTenantId(tenantId);
        String roleName = strRole.startsWith("ROLE_") ? strRole.substring(5) : strRole;
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), tenantId, "admin@metrics.com", "pass", roleName, true);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + roleName)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Verify that parsed metrics are strictly derived from consumer acknowledgements and not inferred")
    void testParsedMetricsNotDerivedFromIngestion() {
        // Step 1: Create 2 emails that are INGESTED
        EmailMessage emailOne = emailRepository.save(new EmailMessage(
                tenant.getId(), mailbox, MailboxProvider.MICROSOFT_365, "msg_dash_01",
                "sender1@test.com", "Sender 1", "PO 101", "Snippet 1", "hash_01", Instant.now()));

        EmailMessage emailTwo = emailRepository.save(new EmailMessage(
                tenant.getId(), mailbox, MailboxProvider.MICROSOFT_365, "msg_dash_02",
                "sender2@test.com", "Sender 2", "PO 102", "Snippet 2", "hash_02", Instant.now()));

        // Step 2: Record an ingestion job showing 5 discovered items
        IngestionJob job = new IngestionJob(tenant.getId(), mailbox, TriggerType.POLLING);
        job.setItemsDiscovered(5);
        job.setItemsIngested(2);
        job.setItemsSkipped(3); // 3 duplicates
        jobRepository.save(job);

        // Step 3: Only Email 1 receives a consumer SUCCESS acknowledgement. Email 2 receives no acknowledgement.
        consumerService.acknowledgeProcessing(emailOne.getId(), new ProcessingAcknowledgementRequest(
                "PO_PROCESSING", ProcessingStatus.SUCCESS, Instant.now(), "PO-101", "Parsed OK", null));

        // Step 4: Outbox event marked as published
        outboxRepository.save(new EventOutbox(
                UUID.randomUUID(), "EMAIL_INGESTED", "EMAIL", emailOne.getId().toString(),
                tenant.getId(), "{}"));
        EventOutbox pubEvent = outboxRepository.findAll().get(0);
        pubEvent.setStatus(OutboxStatus.PUBLISHED);
        outboxRepository.save(pubEvent);

        // Evaluate Tenant Dashboard
        authenticate(tenant.getId(), RoleConstants.TENANT_ADMIN);
        TenantDashboardDto tenantDash = dashboardService.getTenantDashboard(tenant.getId());

        assertEquals(5, tenantDash.emailsDiscovered(), "Discovered must match job summary");
        assertEquals(2, tenantDash.emailsIngested(), "Ingested must match persisted emails");
        assertEquals(1, tenantDash.emailsPublished(), "Published must match outbox published count");
        assertEquals(1, tenantDash.emailsParsedSuccess(), "Parsed must be exactly 1 from consumer ack, NOT 2");
        assertEquals(0, tenantDash.emailsFailed(), "Failed count must be 0");

        // Evaluate Global Dashboard
        authenticate(tenant.getId(), RoleConstants.GLOBAL_ADMIN);
        GlobalDashboardDto globalDash = dashboardService.getGlobalDashboard();

        assertTrue(globalDash.totalEmailsDiscovered() >= 5);
        assertTrue(globalDash.totalEmailsIngested() >= 2);
        assertTrue(globalDash.totalEmailsPublished() >= 1);
        assertEquals(1, globalDash.emailsParsedSuccess(), "Global parsed success must strictly match consumer acks");
    }
}
