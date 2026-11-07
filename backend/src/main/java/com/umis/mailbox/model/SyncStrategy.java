package com.umis.mailbox.model;

/**
 * Strategy used to discover and synchronize incoming emails.
 */
public enum SyncStrategy {
    POLLING,
    WEBHOOK,
    HYBRID
}
