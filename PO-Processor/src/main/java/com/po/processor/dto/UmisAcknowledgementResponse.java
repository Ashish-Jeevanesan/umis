package com.po.processor.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response structure returned by UMIS acknowledgement endpoint.
 */
public record UmisAcknowledgementResponse(
        boolean success,
        String message,
        ProcessingStatusData data
) {
    public record ProcessingStatusData(
            UUID id,
            UUID emailId,
            String consumerCode,
            String status,
            String referenceId,
            String message,
            Instant processedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
