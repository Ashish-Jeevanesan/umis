package com.umis.mailbox.model;

/**
 * Mailbox connection lifecycle and operational status.
 */
public enum MailboxStatus {
    CONNECTED,
    PENDING_AUTHORIZATION,
    SYNCHRONIZING,
    ACTIVE,
    AUTH_EXPIRED,
    CONNECTION_ERROR,
    DISCONNECTED,
    SUSPENDED
}
