package com.po.processor.dto;

/**
 * Summary metrics for the PO Processor monitoring dashboard.
 */
public record PoDashboardSummaryDto(
        long totalReceived,
        long poIdentified,
        long readyForReview,
        long needsReview,
        long verified,
        long approved,
        long duplicate,
        long failed,
        long pending,
        long processing,
        long readyForExtraction,
        long completed
) {
    public PoDashboardSummaryDto(
            long totalReceived,
            long poIdentified,
            long readyForReview,
            long needsReview,
            long verified,
            long approved,
            long duplicate,
            long failed
    ) {
        this(totalReceived, poIdentified, readyForReview, needsReview, verified, approved, duplicate, failed,
                0, 0, readyForReview, approved);
    }

    public PoDashboardSummaryDto(
            long totalReceived,
            long pending,
            long processing,
            long readyForExtraction,
            long completed,
            long failed
    ) {
        this(totalReceived, completed, readyForExtraction, 0, 0, completed, 0, failed,
                pending, processing, readyForExtraction, completed);
    }
}
