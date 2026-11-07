package com.umis.mailbox.connector;

import com.umis.mailbox.connector.model.ConnectionResult;
import com.umis.mailbox.connector.model.FetchResult;
import com.umis.mailbox.connector.model.NormalizedAttachment;
import com.umis.mailbox.connector.model.NormalizedEmail;
import com.umis.mailbox.connector.model.ProviderCapability;
import com.umis.mailbox.connector.model.ProviderConnectionInit;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxCredentials;
import com.umis.mailbox.model.MailboxProvider;

import java.util.List;

/**
 * Common MailboxConnector interface that every provider adapter implements.
 * Ensures the ingestion engine and REST APIs remain completely provider-agnostic.
 */
public interface MailboxConnector {

    MailboxProvider getProvider();

    ProviderCapability getCapability();

    ProviderConnectionInit initiateConnection(String redirectUri, String state);

    ConnectionResult completeConnection(String authorizationCode, String codeVerifier, String redirectUri);

    boolean refreshCredentials(MailboxCredentials credentials);

    boolean testConnection(MailboxConnection connection, MailboxCredentials credentials);

    FetchResult fetchMessages(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String cursor,
            int maxResults
    );

    NormalizedEmail fetchMessage(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String providerMessageId
    );

    List<NormalizedAttachment> fetchAttachments(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String providerMessageId
    );

    String startMonitoring(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String webhookUrl
    );

    boolean stopMonitoring(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String subscriptionId
    );

    void disconnect(MailboxConnection connection, MailboxCredentials credentials);
}
