package com.po.processor.entity;

/**
 * Lifecycle statuses for a Purchase Order processing job.
 */
public enum JobProcessingStatus {
    PENDING,
    READY_FOR_EXTRACTION,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED
}
