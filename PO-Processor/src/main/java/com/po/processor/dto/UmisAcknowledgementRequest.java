package com.po.processor.dto;

import java.time.Instant;

/**
 * Request payload sent to UMIS acknowledgement endpoint:
 * POST /api/v1/emails/{emailId}/acknowledgements
 */
public record UmisAcknowledgementRequest(
        String consumerModule,
        UmisProcessingStatus status,
        Instant processedAt,
        String referenceId,
        String message,
        String idempotencyKey
) {}
