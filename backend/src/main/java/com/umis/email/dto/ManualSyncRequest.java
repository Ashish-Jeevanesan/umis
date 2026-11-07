package com.umis.email.dto;

import com.umis.email.model.TriggerType;

/**
 * Payload for triggering manual or historical synchronization on a mailbox.
 */
public record ManualSyncRequest(
        TriggerType triggerType,
        String historicalRange // NEW_ONLY, LAST_7_DAYS, LAST_30_DAYS, LAST_90_DAYS
) {}
