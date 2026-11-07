package com.umis.email;

import com.umis.email.model.EmailMessage;
import com.umis.email.model.IngestionStatus;
import com.umis.email.repository.EmailMessageRepository;
import com.umis.email.service.EmailIngestionService;
import com.umis.event.model.OutboxStatus;
import com.umis.event.repository.EventOutboxRepository;
import com.umis.mailbox.connector.model.NormalizedAttachment;
import com.umis.mailbox.connector.model.NormalizedEmail;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.SyncStrategy;
import com.umis.mailbox.repository.MailboxConnectionRepository;
import com.umis.tenant.model.Tenant;
import com.umis.tenant.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class EmailIngestionServiceTest {

    @Autowired
    private EmailIngestionService ingestionService;

    @Autowired
    private EmailMessageRepository messageRepository;

    @Autowired
    private EventOutboxRepository outboxRepository;

    @Autowired
    private MailboxConnectionRepository mailboxRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    @DisplayName("Email ingestion normalizes message, persists attachments, and writes outbox event")
    void testIngestMessageWithAttachmentsAndOutbox() {
        Tenant tenant = tenantRepository.save(new Tenant("tenant-test-" + UUID.randomUUID(), "Test Tenant"));
        MailboxConnection mailbox = mailboxRepository.save(new MailboxConnection(
                tenant.getId(), "orders@test.com", "Orders", MailboxProvider.MOCK, SyncStrategy.POLLING));

        byte[] attachmentData = "Invoice content test 12345".getBytes(StandardCharsets.UTF_8);
        NormalizedAttachment attachment = NormalizedAttachment.of(
                "invoice-99.pdf", "application/pdf", attachmentData.length, "dummy_hash_1", attachmentData);

        NormalizedEmail normalized = NormalizedEmail.create(
                tenant.getId(),
                mailbox.getId(),
                MailboxProvider.MOCK,
                "provider-msg-001",
                "vendor@supplier.com",
                "Supplier Vendor",
                List.of("orders@test.com"),
                "New Supplier Order #001",
                "Please process the attached invoice.",
                "<p>Please process the attached invoice.</p>",
                Instant.now(),
                List.of(attachment),
                "hash-001"
        );

        EmailIngestionService.IngestionResult result = ingestionService.processNormalizedEmail(mailbox, normalized);

        assertNotNull(result);
        assertFalse(result.isDuplicate());
        assertEquals("provider-msg-001", result.email().getProviderMessageId());
        assertEquals(IngestionStatus.INGESTED, result.email().getIngestionStatus());
        assertTrue(result.email().isHasAttachments());
        assertEquals(1, result.email().getAttachmentCount());

        // Verify outbox record created in same transaction
        long pendingOutboxCount = outboxRepository.countByStatus(OutboxStatus.PENDING);
        assertTrue(pendingOutboxCount > 0, "Outbox event should be registered with PENDING status");
    }

    @Test
    @DisplayName("Duplicate email with same provider message ID is detected and skipped")
    void testDuplicateEmailDeduplication() {
        Tenant tenant = tenantRepository.save(new Tenant("tenant-dup-" + UUID.randomUUID(), "Dup Tenant"));
        MailboxConnection mailbox = mailboxRepository.save(new MailboxConnection(
                tenant.getId(), "inbox@dup.com", "Inbox", MailboxProvider.MOCK, SyncStrategy.POLLING));

        NormalizedEmail email1 = NormalizedEmail.create(
                tenant.getId(),
                mailbox.getId(),
                MailboxProvider.MOCK,
                "dup-msg-id-100",
                "sender@dup.com",
                "Sender",
                List.of("inbox@dup.com"),
                "Important Message",
                "Body 1",
                "<p>Body 1</p>",
                Instant.now(),
                List.of(),
                "hash-dup-1"
        );

        // First ingestion
        EmailIngestionService.IngestionResult first = ingestionService.processNormalizedEmail(mailbox, email1);
        assertFalse(first.isDuplicate());

        // Second ingestion of same message
        EmailIngestionService.IngestionResult second = ingestionService.processNormalizedEmail(mailbox, email1);
        assertTrue(second.isDuplicate(), "Duplicate message must be detected as duplicate");

        // Verify only 1 record exists in repository
        long totalInDb = messageRepository.count();
        assertEquals(1, totalInDb, "Duplicate email must not create a duplicate row in email_messages");
    }
}
