package com.umis.mailbox.dto;

import com.umis.mailbox.model.MailboxStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Health check status report for a mailbox connection.
 */
public record MailboxHealthDto(
        UUID mailboxId,
        String emailAddress,
        MailboxStatus status,
        boolean isReachable,
        boolean isCredentialsValid,
        Instant lastSuccessfulSync,
        int failureCount,
        String message
) {}
