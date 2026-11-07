package com.umis.email.dto;

import com.umis.email.model.EmailMessage;
import com.umis.email.model.IngestionStatus;
import com.umis.mailbox.model.MailboxProvider;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary DTO for displaying ingested emails in operational activity lists.
 */
public record EmailDto(
        UUID id,
        UUID tenantId,
        UUID mailboxId,
        String mailboxEmail,
        MailboxProvider provider,
        String providerMessageId,
        String sender,
        String senderName,
        String subject,
        String snippet,
        boolean hasAttachments,
        int attachmentCount,
        IngestionStatus ingestionStatus,
        Instant receivedAt,
        Instant createdAt
) {
    public static EmailDto from(EmailMessage entity) {
        return new EmailDto(
                entity.getId(),
                entity.getTenantId(),
                entity.getMailbox().getId(),
                entity.getMailbox().getEmailAddress(),
                entity.getProvider(),
                entity.getProviderMessageId(),
                entity.getSender(),
                entity.getSenderName(),
                entity.getSubject(),
                entity.getSnippet(),
                entity.isHasAttachments(),
                entity.getAttachmentCount(),
                entity.getIngestionStatus(),
                entity.getReceivedAt(),
                entity.getCreatedAt()
        );
    }
}
