package com.po.processor.duplicate.dto;

import com.po.processor.entity.ExtractedPurchaseOrder;

import java.math.BigDecimal;

/**
 * Result representation of duplicate and revision detection.
 */
public record DuplicateCheckResult(
        String duplicateType,
        String detectionReason,
        BigDecimal confidenceScore,
        ExtractedPurchaseOrder originalPurchaseOrder,
        boolean isDuplicate,
        boolean isRevision
) {}
