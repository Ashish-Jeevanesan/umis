package com.umis.mailbox.connector.model;

import java.util.UUID;

/**
 * Normalized provider-independent representation of an email attachment.
 */
public record NormalizedAttachment(
        UUID attachmentId,
        String fileName,
        String contentType,
        long sizeBytes,
        String fileHash,
        String storageReference,
        String contentId,
        boolean isInline,
        byte[] rawBytes // In-memory byte array before object storage persistence
) {
    public static NormalizedAttachment of(
            String fileName,
            String contentType,
            long sizeBytes,
            String fileHash,
            byte[] rawBytes
    ) {
        return new NormalizedAttachment(
                UUID.randomUUID(),
                fileName,
                contentType,
                sizeBytes,
                fileHash,
                null,
                null,
                false,
                rawBytes
        );
    }
}
