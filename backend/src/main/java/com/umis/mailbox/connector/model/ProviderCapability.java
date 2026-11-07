package com.umis.mailbox.connector.model;

import com.umis.mailbox.model.MailboxProvider;

import java.util.List;

/**
 * Capability descriptor for supported email providers exposed via the API.
 */
public record ProviderCapability(
        MailboxProvider providerCode,
        String displayName,
        String connectionType,
        String authMethod,
        List<String> supportedCapabilities,
        boolean supportsWebhooks,
        boolean supportsHistoricalSync
) {}
