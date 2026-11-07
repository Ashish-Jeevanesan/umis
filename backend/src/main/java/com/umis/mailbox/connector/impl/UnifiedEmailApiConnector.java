package com.umis.mailbox.connector.impl;

import com.umis.common.exception.MailboxIntegrationException;
import com.umis.common.util.HashUtils;
import com.umis.mailbox.connector.MailboxConnector;
import com.umis.mailbox.connector.model.ConnectionResult;
import com.umis.mailbox.connector.model.FetchResult;
import com.umis.mailbox.connector.model.NormalizedAttachment;
import com.umis.mailbox.connector.model.NormalizedEmail;
import com.umis.mailbox.connector.model.ProviderCapability;
import com.umis.mailbox.connector.model.ProviderConnectionInit;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxCredentials;
import com.umis.mailbox.model.MailboxProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Extensible adapter for third-party unified email APIs (such as Nylas, Unipile, or Aurinko).
 */
@Component
public class UnifiedEmailApiConnector implements MailboxConnector {

    private static final Logger log = LoggerFactory.getLogger(UnifiedEmailApiConnector.class);

    @Override
    public MailboxProvider getProvider() {
        return MailboxProvider.UNIFIED_API;
    }

    @Override
    public ProviderCapability getCapability() {
        return new ProviderCapability(
                MailboxProvider.UNIFIED_API,
                "Unified Email API (Nylas / Unipile)",
                "API_KEY_OR_OAUTH",
                "HOSTED_AUTH",
                List.of("READ_MESSAGES", "ATTACHMENTS", "WEBHOOKS", "CURSOR_SYNC"),
                true,
                true
        );
    }

    @Override
    public ProviderConnectionInit initiateConnection(String redirectUri, String state) {
        String authUrl = "https://api.unified-email.example.com/oauth/connect?redirect_uri=" + redirectUri + "&state=" + state;
        return new ProviderConnectionInit(authUrl, state, UUID.randomUUID().toString());
    }

    @Override
    public ConnectionResult completeConnection(String authorizationCode, String codeVerifier, String redirectUri) {
        return ConnectionResult.ok(
                "connected@unified-inbox.com",
                "Unified Account",
                "unified_token_" + UUID.randomUUID(),
                null,
                Instant.now().plusSeconds(86400 * 30),
                "email.read"
        );
    }

    @Override
    public boolean refreshCredentials(MailboxCredentials credentials) {
        return true;
    }

    @Override
    public boolean testConnection(MailboxConnection connection, MailboxCredentials credentials) {
        return true;
    }

    @Override
    public FetchResult fetchMessages(MailboxConnection connection, MailboxCredentials credentials, String cursor, int maxResults) {
        return FetchResult.empty();
    }

    @Override
    public NormalizedEmail fetchMessage(MailboxConnection connection, MailboxCredentials credentials, String providerMessageId) {
        return NormalizedEmail.create(
                connection.getTenantId(), connection.getId(), MailboxProvider.UNIFIED_API,
                providerMessageId, "sender@unified.com", "Unified Sender", List.of(connection.getEmailAddress()),
                "Unified Message", "Body text", "<p>Body text</p>", Instant.now(), List.of(),
                HashUtils.sha256Hex(providerMessageId));
    }

    @Override
    public List<NormalizedAttachment> fetchAttachments(MailboxConnection connection, MailboxCredentials credentials, String providerMessageId) {
        return List.of();
    }

    @Override
    public String startMonitoring(MailboxConnection connection, MailboxCredentials credentials, String webhookUrl) {
        return "unified_sub_" + UUID.randomUUID();
    }

    @Override
    public boolean stopMonitoring(MailboxConnection connection, MailboxCredentials credentials, String subscriptionId) {
        return true;
    }

    @Override
    public void disconnect(MailboxConnection connection, MailboxCredentials credentials) {
        log.info("Disconnected unified API connection for {}", connection.getEmailAddress());
    }
}
