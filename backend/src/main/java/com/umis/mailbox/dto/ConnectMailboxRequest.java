package com.umis.mailbox.dto;

import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.SyncStrategy;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Payload for connecting a mailbox (OAuth completion or IMAP direct credentials).
 */
public record ConnectMailboxRequest(
        UUID tenantId, // Optional: if global admin, or derived from authenticated context

        @NotNull(message = "Mailbox provider is required")
        MailboxProvider provider,

        String emailAddress,
        String displayName,
        SyncStrategy syncStrategy,
        Integer pollingIntervalMinutes,

        // For OAuth
        String authorizationCode,
        String codeVerifier,
        String redirectUri,
        String state,

        // For IMAP
        String imapHost,
        Integer imapPort,
        String imapPassword,
        Boolean useSsl
) {}
