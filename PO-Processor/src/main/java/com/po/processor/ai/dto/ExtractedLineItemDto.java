package com.po.processor.ai.dto;

import java.math.BigDecimal;

/**
 * Data transfer object representing an extracted line item from Gemini or document text.
 */
public record ExtractedLineItemDto(
        int lineNumber,
        String productCode,
        String mfgPartNumber,
        String productName,
        String description,
        String category,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal discount,
        BigDecimal taxPercentage,
        BigDecimal taxAmount,
        BigDecimal lineSubtotal,
        BigDecimal lineTotal,
        String currency,
        String batchLotReference,
        String serialNumber
) {}
