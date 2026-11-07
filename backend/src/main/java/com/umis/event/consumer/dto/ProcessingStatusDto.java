package com.umis.event.consumer.dto;

import com.umis.event.consumer.model.EmailProcessingStatus;
import com.umis.event.consumer.model.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Presentation DTO for consumer processing status.
 */
public record ProcessingStatusDto(
        UUID id,
        UUID emailId,
        String consumerCode,
        ProcessingStatus status,
        String referenceId,
        String message,
        Instant processedAt,
        Instant updatedAt
) {
    public static ProcessingStatusDto from(EmailProcessingStatus entity) {
        return new ProcessingStatusDto(
                entity.getId(),
                entity.getEmail().getId(),
                entity.getConsumerCode(),
                entity.getStatus(),
                entity.getReferenceId(),
                entity.getMessage(),
                entity.getProcessedAt(),
                entity.getUpdatedAt()
        );
    }
}
