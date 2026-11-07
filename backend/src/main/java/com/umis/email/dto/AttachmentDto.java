package com.umis.email.dto;

import com.umis.email.model.EmailAttachment;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object representing email attachment metadata.
 */
public record AttachmentDto(
        UUID id,
        UUID emailId,
        String fileName,
        String contentType,
        long sizeBytes,
        String fileHash,
        String storageReference,
        String scanStatus,
        Instant createdAt
) {
    public static AttachmentDto from(EmailAttachment entity) {
        return new AttachmentDto(
                entity.getId(),
                entity.getEmail().getId(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getFileHash(),
                entity.getStorageReference(),
                entity.getScanStatus(),
                entity.getCreatedAt()
        );
    }
}
