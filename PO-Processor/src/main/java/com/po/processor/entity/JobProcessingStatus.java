package com.po.processor.entity;

/**
 * Technical lifecycle statuses for a Purchase Order processing job.
 */
public enum JobProcessingStatus {
    PENDING,
    QUEUED,
    READY_FOR_EXTRACTION,
    PROCESSING,
    READY_FOR_REVIEW,
    DUPLICATE,
    COMPLETED,
    FAILED,
    SKIPPED
}
