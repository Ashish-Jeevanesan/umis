package com.umis.event.consumer.model;

/**
 * Status reported by downstream consumers (e.g. PO extraction, CRM ingestion).
 */
public enum ProcessingStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    REVIEW_REQUIRED,
    IGNORED
}
