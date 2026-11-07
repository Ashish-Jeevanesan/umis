package com.umis.email.model;

/**
 * Ingestion trigger mechanism.
 */
public enum TriggerType {
    POLLING,
    WEBHOOK,
    MANUAL_SYNC,
    HISTORICAL
}
