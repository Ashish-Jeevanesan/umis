package com.umis.mailbox.connector.model;

/**
 * Metadata returned when initiating a provider connection flow (e.g. OAuth authorization URL).
 */
public record ProviderConnectionInit(
        String authorizationUrl,
        String state,
        String codeVerifier
) {}
