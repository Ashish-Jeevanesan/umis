package com.po.processor.dto;

import com.po.processor.entity.ProcessingJob;
import com.po.processor.entity.ProcessingJobAttachment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer object representing a Purchase Order processing job.
 */
public record ProcessingJobResponseDto(
        UUID id,
        UUID tenantId,
        UUID umisEmailId,
        UUID mailboxId,
        UUID eventId,
        String processingStatus,
        int processingAttempts,
        String lastError,
        Instant receivedAt,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        List<AttachmentSummaryDto> attachments
) {
    public record AttachmentSummaryDto(
            UUID id,
            String storageReference,
            String originalFilename,
            String contentType,
            Long fileSize,
            String processingStatus,
            Instant createdAt
    ) {
        public static AttachmentSummaryDto from(ProcessingJobAttachment entity) {
            return new AttachmentSummaryDto(
                    entity.getId(),
                    entity.getStorageReference(),
                    entity.getOriginalFilename(),
                    entity.getContentType(),
                    entity.getFileSize(),
                    entity.getProcessingStatus().name(),
                    entity.getCreatedAt()
            );
        }
    }

    public static ProcessingJobResponseDto from(ProcessingJob job) {
        List<AttachmentSummaryDto> attList = job.getAttachments() != null
                ? job.getAttachments().stream().map(AttachmentSummaryDto::from).toList()
                : List.of();

        return new ProcessingJobResponseDto(
                job.getId(),
                job.getTenantId(),
                job.getUmisEmailId(),
                job.getMailboxId(),
                job.getEventId(),
                job.getProcessingStatus().name(),
                job.getProcessingAttempts(),
                job.getLastError(),
                job.getReceivedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                attList
        );
    }
}
