package com.umis.mailbox.dto;

import com.umis.mailbox.model.SyncStrategy;

/**
 * Payload for updating mailbox synchronization settings.
 */
public record UpdateMailboxSettingsRequest(
        String displayName,
        SyncStrategy syncStrategy,
        Integer pollingIntervalMinutes
) {}
