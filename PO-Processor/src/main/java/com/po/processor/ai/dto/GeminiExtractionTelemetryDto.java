package com.po.processor.ai.dto;

/**
 * Data transfer object encapsulating the extracted PO model and API execution telemetry.
 */
public record GeminiExtractionTelemetryDto(
        ExtractedPurchaseOrderDto purchaseOrderDto,
        int inputTokens,
        int outputTokens,
        String finishReason,
        String rawResponseJson
) {}
