package com.po.processor.comparison.dto;

/**
 * Data transfer object representing the side-by-side comparison of a single PO attribute.
 */
public record FieldComparisonItemDto(
        String fieldName,
        String emailValue,
        String attachmentValue,
        String matchResult,
        String comments
) {}
