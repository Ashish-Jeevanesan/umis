package com.umis.common.exception;

/**
 * Thrown when an error occurs while communicating with a third-party mailbox provider.
 */
public class MailboxIntegrationException extends RuntimeException {
    public MailboxIntegrationException(String message) {
        super(message);
    }

    public MailboxIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
