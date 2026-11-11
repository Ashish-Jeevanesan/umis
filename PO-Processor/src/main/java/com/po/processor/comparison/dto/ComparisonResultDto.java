package com.po.processor.comparison.dto;

import java.util.List;

/**
 * Data transfer object encapsulating the complete email versus attachment comparison findings.
 */
public record ComparisonResultDto(
        String comparisonStatus,
        int conflictCount,
        List<FieldComparisonItemDto> fieldComparisons,
        String comparisonReportJson
) {}
