package com.umis.event.model;

/**
 * Status of an event in the transactional outbox table.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
