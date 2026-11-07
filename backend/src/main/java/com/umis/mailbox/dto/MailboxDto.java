package com.umis.mailbox.dto;

import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.MailboxStatus;
import com.umis.mailbox.model.SyncStrategy;

import java.time.Instant;
import java.util.UUID;

/**
 * Mailbox connection presentation DTO.
 */
public record MailboxDto(
        UUID id,
        UUID tenantId,
        String emailAddress,
        String displayName,
        MailboxProvider provider,
        MailboxStatus status,
        SyncStrategy syncStrategy,
        int pollingIntervalMinutes,
        Instant lastSyncAt,
        Instant lastSuccessfulSyncAt,
        String errorMessage,
        int failureCount,
        Instant createdAt
) {
    public static MailboxDto from(MailboxConnection connection) {
        return new MailboxDto(
                connection.getId(),
                connection.getTenantId(),
                connection.getEmailAddress(),
                connection.getDisplayName(),
                connection.getProvider(),
                connection.getStatus(),
                connection.getSyncStrategy(),
                connection.getPollingIntervalMinutes(),
                connection.getLastSyncAt(),
                connection.getLastSuccessfulSyncAt(),
                connection.getErrorMessage(),
                connection.getFailureCount(),
                connection.getCreatedAt()
        );
    }
}
