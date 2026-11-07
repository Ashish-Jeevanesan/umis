package com.umis.mailbox.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umis.common.exception.MailboxIntegrationException;
import com.umis.config.UmisProperties;
import com.umis.mailbox.connector.impl.MicrosoftGraphConnector;
import com.umis.mailbox.connector.model.ConnectionResult;
import com.umis.mailbox.connector.model.FetchResult;
import com.umis.mailbox.connector.model.NormalizedAttachment;
import com.umis.mailbox.connector.model.NormalizedEmail;
import com.umis.mailbox.connector.model.ProviderConnectionInit;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxCredentials;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.SyncStrategy;
import com.umis.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MicrosoftGraphConnectorTest {

    private MicrosoftGraphConnector connector;
    private MockRestServiceServer mockServer;
    private EncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MailboxConnection connection;
    private MailboxCredentials credentials;

    @BeforeEach
    void setUp() {
        UmisProperties properties = new UmisProperties();
        properties.getSecurity().setEncryptionKey("test-32-character-secret-key-ok!");
        encryptionService = new EncryptionService(properties);

        connector = new MicrosoftGraphConnector(encryptionService, objectMapper);
        ReflectionTestUtils.setField(connector, "clientId", "test-client-id");
        ReflectionTestUtils.setField(connector, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(connector, "tenantId", "common");

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(connector, "restTemplate");
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();

        UUID tenantId = UUID.randomUUID();
        connection = new MailboxConnection(
                tenantId, "executive@acme.com", "Executive",
                MailboxProvider.MICROSOFT_365, SyncStrategy.POLLING);
        connection.setId(UUID.randomUUID());

        credentials = new MailboxCredentials(connection, "OAUTH2");
        credentials.setEncryptedAccessToken(encryptionService.encrypt("valid_graph_access_token"));
        credentials.setEncryptedRefreshToken(encryptionService.encrypt("valid_graph_refresh_token"));
        credentials.setTokenExpiresAt(Instant.now().plusSeconds(3600));
    }

    @Test
    @DisplayName("Microsoft Graph: OAuth initiation creates authorization URL with PKCE and state")
    void testInitiateConnection() {
        ProviderConnectionInit init = connector.initiateConnection("http://localhost/callback", "csrf_state_123");
        assertNotNull(init.authorizationUrl());
        assertTrue(init.authorizationUrl().contains("client_id=test-client-id"));
        assertTrue(init.authorizationUrl().contains("state=csrf_state_123"));
        assertTrue(init.authorizationUrl().contains("scope="));
        assertNotNull(init.codeVerifier());
    }

    @Test
    @DisplayName("Microsoft Graph: Complete OAuth connection exchanges token and loads user profile")
    void testCompleteConnection() {
        String strTokenResponse = """
            {
              "access_token": "mock_token_abc",
              "refresh_token": "mock_refresh_xyz",
              "expires_in": 3600
            }
            """;

        String strMeResponse = """
            {
              "mail": "executive@acme.com",
              "displayName": "Executive User",
              "userPrincipalName": "executive@acme.com"
            }
            """;

        mockServer.expect(requestTo("https://login.microsoftonline.com/common/oauth2/v2.0/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(strTokenResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://graph.microsoft.com/v1.0/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer mock_token_abc"))
                .andRespond(withSuccess(strMeResponse, MediaType.APPLICATION_JSON));

        ConnectionResult result = connector.completeConnection(
                "auth_code_123", "code_verifier_123", "http://localhost/callback");

        assertTrue(result.success());
        assertEquals("executive@acme.com", result.emailAddress());
        assertEquals("Executive User", result.displayName());
        assertEquals("mock_token_abc", result.accessToken());
        mockServer.verify();
    }

    @Test
    @DisplayName("Microsoft Graph: Delta synchronization fetches messages and parses next deltaLink")
    void testFetchMessagesDelta() {
        String strDeltaResponse = """
            {
              "@odata.deltaLink": "https://graph.microsoft.com/v1.0/me/mailFolders/inbox/messages/delta?$deltatoken=xyz789",
              "value": [
                {
                  "id": "graph_msg_001",
                  "subject": "Q3 Financial Statement",
                  "from": {
                    "emailAddress": {
                      "name": "Accounting",
                      "address": "accounting@acme.com"
                    }
                  },
                  "body": {
                    "contentType": "html",
                    "content": "<p>Please review attached statement</p>"
                  },
                  "receivedDateTime": "2026-11-03T10:00:00Z",
                  "hasAttachments": false
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://graph.microsoft.com/v1.0/me/mailFolders/inbox/messages/delta?$top=50"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer valid_graph_access_token"))
                .andRespond(withSuccess(strDeltaResponse, MediaType.APPLICATION_JSON));

        FetchResult result = connector.fetchMessages(connection, credentials, null, 50);

        assertNotNull(result);
        assertEquals(1, result.messages().size());
        NormalizedEmail email = result.messages().get(0);
        assertEquals("graph_msg_001", email.providerMessageId());
        assertEquals("Q3 Financial Statement", email.subject());
        assertEquals("accounting@acme.com", email.sender());
        assertTrue(result.newCursor().contains("$deltatoken=xyz789"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Microsoft Graph: Expired delta cursor (410 Gone / resyncRequired) triggers recovery exception")
    void testExpiredDeltaCursorThrowsRecoveryException() {
        mockServer.expect(requestTo("https://graph.microsoft.com/v1.0/me/mailFolders/inbox/messages/delta?$deltatoken=stale"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.GONE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"code\": \"resyncRequired\", \"message\": \"Delta cursor expired\"}}"));

        MailboxIntegrationException exception = assertThrows(
                MailboxIntegrationException.class,
                () -> connector.fetchMessages(connection, credentials,
                        "https://graph.microsoft.com/v1.0/me/mailFolders/inbox/messages/delta?$deltatoken=stale", 50)
        );

        assertTrue(exception.getMessage().contains("delta cursor expired")
                || exception.getMessage().contains("resyncRequired"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Microsoft Graph: Rate limit 429 Too Many Requests triggers rate limit exception")
    void testRateLimitThrowsException() {
        mockServer.expect(requestTo("https://graph.microsoft.com/v1.0/me/mailFolders/inbox/messages/delta?$top=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"code\": \"activityLimitReached\", \"message\": \"Throttled\"}}"));

        MailboxIntegrationException exception = assertThrows(
                MailboxIntegrationException.class,
                () -> connector.fetchMessages(connection, credentials, null, 50)
        );

        assertTrue(exception.getMessage().contains("429")
                || exception.getMessage().contains("rate limit"));
        mockServer.verify();
    }
}
