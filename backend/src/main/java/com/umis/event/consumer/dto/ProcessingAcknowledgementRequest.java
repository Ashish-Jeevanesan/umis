package com.umis.event.consumer.dto;

import com.umis.event.consumer.model.ProcessingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Payload sent by external consumers to acknowledge processing outcome.
 */
public record ProcessingAcknowledgementRequest(
        @NotBlank(message = "Consumer module code is required")
        String consumerModule,

        @NotNull(message = "Processing status is required")
        ProcessingStatus status,

        Instant processedAt,
        String referenceId,
        String message,
        String idempotencyKey
) {}
