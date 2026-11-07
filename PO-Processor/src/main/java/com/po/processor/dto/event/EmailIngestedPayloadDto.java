package com.po.processor.dto.event;

import java.util.List;
import java.util.UUID;

/**
 * Mirror of the payload carried by EMAIL_INGESTED events from UMIS.
 */
public record EmailIngestedPayloadDto(
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
        List<AttachmentReferenceDto> attachments
) {
    public record AttachmentReferenceDto(
            UUID attachmentId,
            String fileName,
            String contentType,
            long size,
            String storageReference,
            String fileHash
    ) {}
}
