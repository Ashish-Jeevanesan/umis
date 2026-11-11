package com.po.processor.validation.dto;

import com.po.processor.entity.PoValidationResult;

import java.util.List;

/**
 * Summary of all mathematical and business validation rules executed against a purchase order.
 */
public record ValidationSummaryDto(
        boolean isValid,
        int errorCount,
        int warningCount,
        List<PoValidationResult> validationResults
) {}
