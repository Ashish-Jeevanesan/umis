package com.umis.email.model;

/**
 * Lifecycle status of an email during the ingestion phase.
 */
public enum IngestionStatus {
    DISCOVERED,
    FETCHING,
    INGESTED,
    PUBLISHED,
    FAILED,
    DUPLICATE,
    IGNORED
}
