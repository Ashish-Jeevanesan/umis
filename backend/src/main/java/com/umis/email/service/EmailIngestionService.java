package com.umis.email.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umis.email.model.EmailAttachment;
import com.umis.email.model.EmailMessage;
import com.umis.email.model.EmailRecipient;
import com.umis.email.model.IngestionStatus;
import com.umis.email.repository.EmailAttachmentRepository;
import com.umis.email.repository.EmailMessageRepository;
import com.umis.email.repository.EmailRecipientRepository;
import com.umis.event.model.EmailIngestedPayload;
import com.umis.event.model.EventOutbox;
import com.umis.event.model.UmisEvent;
import com.umis.event.repository.EventOutboxRepository;
import com.umis.mailbox.connector.model.NormalizedAttachment;
import com.umis.mailbox.connector.model.NormalizedEmail;
import com.umis.mailbox.model.MailboxConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing normalization, deduplication, S3 object persistence, and transactional outbox creation.
 */
@Service
public class EmailIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EmailIngestionService.class);

    private final EmailMessageRepository messageRepository;
    private final EmailRecipientRepository recipientRepository;
    private final EmailAttachmentRepository attachmentRepository;
    private final EmailStorageService storageService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public EmailIngestionService(
            EmailMessageRepository messageRepository,
            EmailRecipientRepository recipientRepository,
            EmailAttachmentRepository attachmentRepository,
            EmailStorageService storageService,
            EventOutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.messageRepository = messageRepository;
        this.recipientRepository = recipientRepository;
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestionResult processNormalizedEmail(MailboxConnection mailbox, NormalizedEmail normalized) {
        // Step 1: Deduplication Check
        Optional<EmailMessage> existing = messageRepository.findByMailboxIdAndProviderMessageId(
                mailbox.getId(), normalized.providerMessageId());
        if (existing.isPresent()) {
            log.debug("Duplicate email skipped: mailbox={}, providerMessageId={}",
                    mailbox.getId(), normalized.providerMessageId());
            return IngestionResult.duplicate(existing.get());
        }

        // Step 2: Persist Email Entity
        EmailMessage email = new EmailMessage(
                mailbox.getTenantId(),
                mailbox,
                mailbox.getProvider(),
                normalized.providerMessageId(),
                normalized.sender(),
                normalized.senderName(),
                normalized.subject(),
                normalized.snippet(),
                normalized.contentHash(),
                normalized.receivedAt()
        );
        email.setBodyHtmlAvailable(normalized.bodyHtmlAvailable());
        EmailMessage savedEmail = messageRepository.save(email);

        // Step 3: Store Body Text & HTML in S3
        String textRef = storageService.storeEmailBody(
                mailbox.getTenantId(), mailbox.getId(), savedEmail.getId(), normalized.bodyText(), "txt");
        String htmlRef = storageService.storeEmailBody(
                mailbox.getTenantId(), mailbox.getId(), savedEmail.getId(), normalized.bodyHtml(), "html");
        savedEmail.setBodyTextStorageRef(textRef);
        savedEmail.setBodyHtmlStorageRef(htmlRef);

        // Step 4: Save Recipients
        saveRecipients(savedEmail, normalized);

        // Step 5: Save Attachments
        List<EmailIngestedPayload.AttachmentReference> attachmentRefs = saveAttachments(savedEmail, mailbox, normalized);
        savedEmail.setHasAttachments(!attachmentRefs.isEmpty());
        savedEmail.setAttachmentCount(attachmentRefs.size());
        EmailMessage finalEmail = messageRepository.save(savedEmail);

        // Step 6: Create Transactional Outbox Event
        createOutboxEvent(finalEmail, mailbox, normalized, attachmentRefs);

        return IngestionResult.ingested(finalEmail);
    }

    private void saveRecipients(EmailMessage email, NormalizedEmail normalized) {
        if (normalized.recipients() != null) {
            for (String toAddress : normalized.recipients()) {
                recipientRepository.save(new EmailRecipient(email, "TO", toAddress, toAddress));
            }
        }
        if (normalized.cc() != null) {
            for (String ccAddress : normalized.cc()) {
                recipientRepository.save(new EmailRecipient(email, "CC", ccAddress, ccAddress));
            }
        }
        if (normalized.bcc() != null) {
            for (String bccAddress : normalized.bcc()) {
                recipientRepository.save(new EmailRecipient(email, "BCC", bccAddress, bccAddress));
            }
        }
    }

    private List<EmailIngestedPayload.AttachmentReference> saveAttachments(
            EmailMessage email, MailboxConnection mailbox, NormalizedEmail normalized) {
        List<EmailIngestedPayload.AttachmentReference> refs = new ArrayList<>();
        if (normalized.attachments() == null || normalized.attachments().isEmpty()) {
            return refs;
        }

        for (NormalizedAttachment att : normalized.attachments()) {
            UUID attId = UUID.randomUUID();
            String storageRef = storageService.storeAttachment(
                    mailbox.getTenantId(), email.getId(), attId, att.rawBytes(), att.contentType());

            EmailAttachment entity = new EmailAttachment(
                    email, mailbox.getTenantId(), att.fileName(), att.contentType(),
                    att.sizeBytes(), att.fileHash(), storageRef != null ? storageRef : "pending/" + attId);
            EmailAttachment savedAtt = attachmentRepository.save(entity);

            refs.add(new EmailIngestedPayload.AttachmentReference(
                    savedAtt.getId(), att.fileName(), att.contentType(),
                    att.sizeBytes(), entity.getStorageReference(), att.fileHash()));
        }
        return refs;
    }

    private void createOutboxEvent(
            EmailMessage email,
            MailboxConnection mailbox,
            NormalizedEmail normalized,
            List<EmailIngestedPayload.AttachmentReference> attachmentRefs
    ) {
        try {
            EmailIngestedPayload payload = new EmailIngestedPayload(
                    email.getId(),
                    mailbox.getId(),
                    mailbox.getTenantId(),
                    email.getSubject(),
                    email.getSender(),
                    email.getSenderName(),
                    normalized.recipients(),
                    email.getSnippet(),
                    email.getBodyTextStorageRef(),
                    email.getBodyHtmlStorageRef(),
                    email.isHasAttachments(),
                    attachmentRefs
            );

            UmisEvent<EmailIngestedPayload> event = UmisEvent.of(
                    "EMAIL_INGESTED", mailbox.getTenantId(), mailbox.getId(), payload);
            String payloadJson = objectMapper.writeValueAsString(event);

            EventOutbox outbox = new EventOutbox(
                    event.eventId(),
                    event.eventType(),
                    "EMAIL",
                    email.getId().toString(),
                    mailbox.getTenantId(),
                    payloadJson
            );
            outboxRepository.save(outbox);
        } catch (Exception exception) {
            log.error("Failed to serialize outbox event for email {}: {}", email.getId(), exception.getMessage());
            throw new IllegalStateException("Failed to create outbox event for email: " + email.getId(), exception);
        }
    }

    public record IngestionResult(EmailMessage email, boolean isDuplicate) {
        public static IngestionResult ingested(EmailMessage email) {
            return new IngestionResult(email, false);
        }

        public static IngestionResult duplicate(EmailMessage email) {
            return new IngestionResult(email, true);
        }
    }
}
