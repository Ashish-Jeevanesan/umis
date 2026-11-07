package com.umis.mailbox.connector;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.umis.config.UmisProperties;
import com.umis.mailbox.connector.impl.GenericImapConnector;
import com.umis.mailbox.connector.model.FetchResult;
import com.umis.mailbox.connector.model.NormalizedEmail;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxCredentials;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.SyncStrategy;
import com.umis.security.EncryptionService;
import com.umis.security.ImapHostValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericImapConnectorTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

    private GenericImapConnector connector;
    private EncryptionService encryptionService;
    private ImapHostValidator hostValidator;

    private MailboxConnection connection;
    private MailboxCredentials credentials;

    @BeforeEach
    void setUp() {
        UmisProperties properties = new UmisProperties();
        properties.getSecurity().setEncryptionKey("test-32-character-secret-key-ok!");
        encryptionService = new EncryptionService(properties);

        hostValidator = new ImapHostValidator();
        ReflectionTestUtils.setField(hostValidator, "allowLocalImapHosts", true);

        connector = new GenericImapConnector(encryptionService, hostValidator);

        // Configure GreenMail test user
        greenMail.setUser("testuser@localmail.com", "imap_secret_pass");

        UUID tenantId = UUID.randomUUID();
        connection = new MailboxConnection(
                tenantId, "testuser@localmail.com", "Test User",
                MailboxProvider.GENERIC_IMAP, SyncStrategy.POLLING);
        connection.setId(UUID.randomUUID());

        credentials = new MailboxCredentials(connection, "PASSWORD");
        credentials.setServerHost("127.0.0.1");
        credentials.setServerPort(greenMail.getImap().getPort());
        credentials.setUseSsl(false);
        credentials.setEncryptedSecret(encryptionService.encrypt("imap_secret_pass"));
    }

    @Test
    @DisplayName("IMAP: Live connection test succeeds with valid credentials against IMAP server")
    void testConnectionSuccess() {
        boolean isConnected = connector.testConnection(connection, credentials);
        assertTrue(isConnected, "IMAP connector must successfully connect to active IMAP store");
    }

    @Test
    @DisplayName("IMAP: Connection test fails with invalid password")
    void testConnectionFailureInvalidPassword() {
        credentials.setEncryptedSecret(encryptionService.encrypt("wrong_password"));
        boolean isConnected = connector.testConnection(connection, credentials);
        assertFalse(isConnected, "IMAP connector must fail connection when invalid credentials provided");
    }

    @Test
    @DisplayName("IMAP: Fetch messages incrementally using UID synchronization and prevent duplicates")
    void testFetchMessagesAndIncrementalUidSync() {
        // Send email message to GreenMail IMAP user via SMTP
        GreenMailUtil.sendTextEmail(
                "testuser@localmail.com",
                "vendor@acme.com",
                "Purchase Order Confirmation #7741",
                "Please find confirmed purchase order items for batch 1.",
                greenMail.getSmtp().getServerSetup());

        // Initial fetch with cursor 0
        FetchResult firstResult = connector.fetchMessages(connection, credentials, "0", 10);
        assertNotNull(firstResult);
        assertEquals(1, firstResult.messages().size());

        NormalizedEmail email = firstResult.messages().get(0);
        assertEquals("Purchase Order Confirmation #7741", email.subject());
        assertTrue(email.bodyText().contains("Please find confirmed purchase order"));
        assertNotNull(firstResult.newCursor());

        // Subsequent fetch with new cursor must yield 0 messages (UID duplicate prevention)
        FetchResult secondResult = connector.fetchMessages(connection, credentials, firstResult.newCursor(), 10);
        assertNotNull(secondResult);
        assertEquals(0, secondResult.messages().size(), "Incremental UID sync must not return previously seen UIDs");
    }
}
