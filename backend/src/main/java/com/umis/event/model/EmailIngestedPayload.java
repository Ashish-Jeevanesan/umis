package com.umis.event.model;

import java.util.List;
import java.util.UUID;

/**
 * Payload carried by EMAIL_INGESTED event notifications.
 */
public record EmailIngestedPayload(
        UUID emailId,
        UUID mailboxId,
        UUID tenantId,
        String subject,
        String sender,
        String senderName,
        List<String> recipients,
        String snippet,
        String bodyTextStorageRef,
        String bodyHtmlStorageRef,
        boolean hasAttachments,
        List<AttachmentReference> attachments
) {
    public record AttachmentReference(
            UUID attachmentId,
            String fileName,
            String contentType,
            long size,
            String storageReference,
            String fileHash
    ) {}
}
