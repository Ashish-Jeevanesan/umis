package com.umis.mailbox.connector.impl;

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
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock mailbox connector for zero-external-dependency local testing, demos, and automated testing.
 */
@Component
public class MockMailboxConnector implements MailboxConnector {

    private final AtomicInteger messageSequence = new AtomicInteger(100);

    @Override
    public MailboxProvider getProvider() {
        return MailboxProvider.MOCK;
    }

    @Override
    public ProviderCapability getCapability() {
        return new ProviderCapability(
                MailboxProvider.MOCK,
                "Simulated / Mock Mailbox",
                "SIMULATION",
                "MOCK_AUTH",
                List.of("READ_MESSAGES", "ATTACHMENTS", "INCREMENTAL_SYNC", "WEBHOOKS", "HISTORICAL_SYNC"),
                true,
                true
        );
    }

    @Override
    public ProviderConnectionInit initiateConnection(String redirectUri, String state) {
        String mockAuthUrl = redirectUri + "?code=mock_code_" + UUID.randomUUID() + "&state=" + state;
        return new ProviderConnectionInit(mockAuthUrl, state, "mock_verifier");
    }

    @Override
    public ConnectionResult completeConnection(String authorizationCode, String codeVerifier, String redirectUri) {
        return ConnectionResult.ok(
                "demo-inbox@umis-mock.internal",
                "Demo Mailbox",
                "mock_access_token_" + UUID.randomUUID(),
                "mock_refresh_token_" + UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.DAYS),
                "Mail.Read Mail.ReadBasic"
        );
    }

    @Override
    public boolean refreshCredentials(MailboxCredentials credentials) {
        credentials.setEncryptedAccessToken("mock_refreshed_token_" + UUID.randomUUID());
        credentials.setTokenExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        return true;
    }

    @Override
    public boolean testConnection(MailboxConnection connection, MailboxCredentials credentials) {
        return true;
    }

    @Override
    public FetchResult fetchMessages(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String cursor,
            int maxResults
    ) {
        List<NormalizedEmail> messages = new ArrayList<>();
        int count = Math.min(maxResults > 0 ? maxResults : 3, 5);

        for (int index = 0; index < count; index++) {
            int seq = messageSequence.incrementAndGet();
            String msgId = "mock-msg-" + seq;
            String subject = "Business Order Notice #" + seq;
            String bodyText = "Hello Team,\n\nPlease find the attached document for order #" + seq +
                    ".\nDelivery required by next Monday.\n\nBest regards,\nSupplier Operations";
            String bodyHtml = "<html><body><p>Hello Team,</p><p>Please find the attached document for order #" +
                    seq + ".</p><p>Delivery required by next Monday.</p></body></html>";

            byte[] pdfBytes = ("%PDF-1.4 Mock document bytes for order #" + seq)
                    .getBytes(StandardCharsets.UTF_8);
            String pdfHash = HashUtils.sha256Hex(pdfBytes);
            NormalizedAttachment attachment = NormalizedAttachment.of(
                    "Order-" + seq + ".pdf",
                    "application/pdf",
                    pdfBytes.length,
                    pdfHash,
                    pdfBytes
            );

            String contentHash = HashUtils.sha256Hex(connection.getId() + ":" + msgId + ":" + subject);

            NormalizedEmail email = NormalizedEmail.create(
                    connection.getTenantId(),
                    connection.getId(),
                    MailboxProvider.MOCK,
                    msgId,
                    "supplier-" + seq + "@partner.example.com",
                    "Partner Operations",
                    List.of(connection.getEmailAddress()),
                    subject,
                    bodyText,
                    bodyHtml,
                    Instant.now().minus(index * 10L, ChronoUnit.MINUTES),
                    List.of(attachment),
                    contentHash
            );
            messages.add(email);
        }

        String nextCursor = "cursor_" + System.currentTimeMillis();
        return FetchResult.of(messages, nextCursor, false);
    }

    @Override
    public NormalizedEmail fetchMessage(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String providerMessageId
    ) {
        String subject = "Detail for " + providerMessageId;
        String body = "Body for mock message " + providerMessageId;
        return NormalizedEmail.create(
                connection.getTenantId(),
                connection.getId(),
                MailboxProvider.MOCK,
                providerMessageId,
                "sender@example.com",
                "Mock Sender",
                List.of(connection.getEmailAddress()),
                subject,
                body,
                "<p>" + body + "</p>",
                Instant.now(),
                List.of(),
                HashUtils.sha256Hex(providerMessageId)
        );
    }

    @Override
    public List<NormalizedAttachment> fetchAttachments(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String providerMessageId
    ) {
        byte[] sampleBytes = "Sample document content".getBytes(StandardCharsets.UTF_8);
        return List.of(NormalizedAttachment.of(
                "Document-" + providerMessageId + ".pdf",
                "application/pdf",
                sampleBytes.length,
                HashUtils.sha256Hex(sampleBytes),
                sampleBytes
        ));
    }

    @Override
    public String startMonitoring(MailboxConnection connection, MailboxCredentials credentials, String webhookUrl) {
        return "mock_sub_" + UUID.randomUUID();
    }

    @Override
    public boolean stopMonitoring(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String subscriptionId
    ) {
        return true;
    }

    @Override
    public void disconnect(MailboxConnection connection, MailboxCredentials credentials) {
        // No-op for mock
    }
}
