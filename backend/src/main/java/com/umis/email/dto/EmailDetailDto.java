package com.umis.email.dto;

import com.umis.email.model.EmailMessage;
import com.umis.email.model.IngestionStatus;
import com.umis.mailbox.model.MailboxProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed DTO for drill-down views including recipient lists and attachment references.
 */
public record EmailDetailDto(
        UUID id,
        UUID tenantId,
        UUID mailboxId,
        String mailboxEmail,
        MailboxProvider provider,
        String providerMessageId,
        String sender,
        String senderName,
        List<String> recipients,
        String subject,
        String snippet,
        String bodyTextStorageRef,
        String bodyHtmlStorageRef,
        boolean bodyHtmlAvailable,
        boolean hasAttachments,
        int attachmentCount,
        IngestionStatus ingestionStatus,
        Instant receivedAt,
        Instant createdAt,
        List<AttachmentDto> attachments
) {
    public static EmailDetailDto of(
            EmailMessage entity,
            List<String> recipients,
            List<AttachmentDto> attachments
    ) {
        return new EmailDetailDto(
                entity.getId(),
                entity.getTenantId(),
                entity.getMailbox().getId(),
                entity.getMailbox().getEmailAddress(),
                entity.getProvider(),
                entity.getProviderMessageId(),
                entity.getSender(),
                entity.getSenderName(),
                recipients,
                entity.getSubject(),
                entity.getSnippet(),
                entity.getBodyTextStorageRef(),
                entity.getBodyHtmlStorageRef(),
                entity.isBodyHtmlAvailable(),
                entity.isHasAttachments(),
                entity.getAttachmentCount(),
                entity.getIngestionStatus(),
                entity.getReceivedAt(),
                entity.getCreatedAt(),
                attachments
        );
    }
}
