package com.po.processor.dto;

/**
 * Summary metrics for the PO Processor monitoring dashboard.
 */
public record PoDashboardSummaryDto(
        long totalReceived,
        long pending,
        long processing,
        long readyForExtraction,
        long completed,
        long failed
) {}
