package com.umis.mailbox.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umis.common.exception.MailboxIntegrationException;
import com.umis.config.UmisProperties;
import com.umis.mailbox.connector.impl.GmailConnector;
import com.umis.mailbox.connector.impl.GmailPayloadParser;
import com.umis.mailbox.connector.model.ConnectionResult;
import com.umis.mailbox.connector.model.FetchResult;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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

class GmailConnectorTest {

    private GmailConnector connector;
    private MockRestServiceServer mockServer;
    private EncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GmailPayloadParser payloadParser = new GmailPayloadParser();

    private MailboxConnection connection;
    private MailboxCredentials credentials;

    @BeforeEach
    void setUp() {
        UmisProperties properties = new UmisProperties();
        properties.getSecurity().setEncryptionKey("test-32-character-secret-key-ok!");
        encryptionService = new EncryptionService(properties);

        connector = new GmailConnector(encryptionService, objectMapper, payloadParser);
        ReflectionTestUtils.setField(connector, "clientId", "test-google-client-id");
        ReflectionTestUtils.setField(connector, "clientSecret", "test-google-client-secret");

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(connector, "restTemplate");
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();

        UUID tenantId = UUID.randomUUID();
        connection = new MailboxConnection(
                tenantId, "sales@workspace.com", "Workspace Sales",
                MailboxProvider.GMAIL, SyncStrategy.POLLING);
        connection.setId(UUID.randomUUID());

        credentials = new MailboxCredentials(connection, "OAUTH2");
        credentials.setEncryptedAccessToken(encryptionService.encrypt("valid_gmail_access_token"));
        credentials.setEncryptedRefreshToken(encryptionService.encrypt("valid_gmail_refresh_token"));
        credentials.setTokenExpiresAt(Instant.now().plusSeconds(3600));
    }

    @Test
    @DisplayName("Gmail: OAuth initiation creates authorization URL with offline access and prompt consent")
    void testInitiateConnection() {
        ProviderConnectionInit init = connector.initiateConnection("http://localhost/callback", "gmail_state_999");
        assertNotNull(init.authorizationUrl());
        assertTrue(init.authorizationUrl().contains("client_id=test-google-client-id"));
        assertTrue(init.authorizationUrl().contains("state=gmail_state_999"));
        assertTrue(init.authorizationUrl().contains("access_type=offline"));
    }

    @Test
    @DisplayName("Gmail: Complete OAuth connection exchanges code and fetches Gmail user profile")
    void testCompleteConnection() {
        String strTokenResponse = """
            {
              "access_token": "google_token_123",
              "refresh_token": "google_refresh_456",
              "expires_in": 3600
            }
            """;

        String strProfileResponse = """
            {
              "emailAddress": "sales@workspace.com",
              "messagesTotal": 1500,
              "historyId": "998877"
            }
            """;

        mockServer.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(strTokenResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/profile"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer google_token_123"))
                .andRespond(withSuccess(strProfileResponse, MediaType.APPLICATION_JSON));

        ConnectionResult result = connector.completeConnection(
                "google_code", "verifier", "http://localhost/callback");

        assertTrue(result.success());
        assertEquals("sales@workspace.com", result.emailAddress());
        assertEquals("google_token_123", result.accessToken());
        mockServer.verify();
    }

    @Test
    @DisplayName("Gmail: Fetch and parse multipart MIME message with base64url plain and HTML bodies")
    void testFetchMessageWithMimeParsing() {
        String strPlainText = "Hello, please find purchase order attached.";
        String strHtmlText = "<p>Hello, please find purchase order attached.</p>";
        String strEncodedPlain = Base64.getUrlEncoder().withoutPadding().encodeToString(
                strPlainText.getBytes(StandardCharsets.UTF_8));
        String strEncodedHtml = Base64.getUrlEncoder().withoutPadding().encodeToString(
                strHtmlText.getBytes(StandardCharsets.UTF_8));

        String strMessageResponse = String.format("""
            {
              "id": "gmail_msg_505",
              "internalDate": "1730635200000",
              "snippet": "Hello, please find...",
              "payload": {
                "headers": [
                  {"name": "Subject", "value": "PO-9921 from MegaCorp"},
                  {"name": "From", "value": "buyer@megacorp.com"}
                ],
                "mimeType": "multipart/alternative",
                "parts": [
                  {
                    "mimeType": "text/plain",
                    "body": {"data": "%s"}
                  },
                  {
                    "mimeType": "text/html",
                    "body": {"data": "%s"}
                  }
                ]
              }
            }
            """, strEncodedPlain, strEncodedHtml);

        mockServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/gmail_msg_505?format=full"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer valid_gmail_access_token"))
                .andRespond(withSuccess(strMessageResponse, MediaType.APPLICATION_JSON));

        NormalizedEmail email = connector.fetchMessage(connection, credentials, "gmail_msg_505");

        assertNotNull(email);
        assertEquals("gmail_msg_505", email.providerMessageId());
        assertEquals("PO-9921 from MegaCorp", email.subject());
        assertEquals("buyer@megacorp.com", email.sender());
        assertEquals(strPlainText, email.bodyText());
        assertEquals(strHtmlText, email.bodyHtml());
        mockServer.verify();
    }

    @Test
    @DisplayName("Gmail: Expired history ID returns 404/400 and throws cursor expired exception")
    void testExpiredHistoryIdCursorThrowsException() {
        mockServer.expect(requestTo(
                        "https://gmail.googleapis.com/gmail/v1/users/me/history?startHistoryId=12345&historyTypes=messageAdded&maxResults=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"code\": 404, \"message\": \"HistoryId not found or expired\"}}"));

        MailboxIntegrationException exception = assertThrows(
                MailboxIntegrationException.class,
                () -> connector.fetchMessages(connection, credentials, "h:12345", 20)
        );

        assertTrue(exception.getMessage().contains("cursor expired")
                || exception.getMessage().contains("HistoryId"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Gmail: a plain /messages.list page token (even if all-digits) is never misrouted to the History API")
    void testNumericLookingPageTokenUsesListingNotHistory() {
        // Regression test: a prior version of this connector guessed a cursor's meaning from its
        // format (digits-only => historyId), but Gmail's list page tokens are opaque and are not
        // guaranteed to contain letters. A page token must only ever be treated as a history
        // checkpoint when explicitly marked as one by this connector's own "h:" prefix.
        String strListResponse = """
            {
              "messages": [],
              "resultSizeEstimate": 0
            }
            """;

        mockServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=20&pageToken=08675309"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(strListResponse, MediaType.APPLICATION_JSON));

        String strProfileResponse = """
            {
              "emailAddress": "sales@workspace.com",
              "historyId": "555000"
            }
            """;
        mockServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/profile"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(strProfileResponse, MediaType.APPLICATION_JSON));

        FetchResult result = connector.fetchMessages(connection, credentials, "08675309", 20);

        assertTrue(result.messages().isEmpty());
        assertEquals("h:555000", result.newCursor());
        assertFalse(result.hasMore());
        mockServer.verify();
    }

    @Test
    @DisplayName("Gmail: switches to incremental History API checking after one page, even when more historical pages remain")
    void testListingDoesNotKeepPagingThroughFullHistory() {
        // Regression test: a prior version kept following nextPageToken indefinitely, so an
        // account with substantial mail could spend many sync cycles paging backward through old
        // messages before ever noticing genuinely new mail. It must now re-baseline onto the
        // current historyId after a single page regardless of whether the listing itself reports
        // more pages are available.
        String strListResponse = """
            {
              "messages": [{"id": "gmail_msg_old_1"}],
              "nextPageToken": "there-is-more-history-after-this",
              "resultSizeEstimate": 500
            }
            """;
        mockServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(strListResponse, MediaType.APPLICATION_JSON));

        String strMessageResponse = """
            {
              "id": "gmail_msg_old_1",
              "internalDate": "1730635200000",
              "snippet": "old message",
              "payload": { "headers": [
                {"name": "Subject", "value": "Old message"},
                {"name": "From", "value": "someone@example.com"}
              ] }
            }
            """;
        mockServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/gmail_msg_old_1?format=full"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(strMessageResponse, MediaType.APPLICATION_JSON));

        String strProfileResponse = """
            {
              "emailAddress": "sales@workspace.com",
              "historyId": "777000"
            }
            """;
        mockServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/profile"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(strProfileResponse, MediaType.APPLICATION_JSON));

        FetchResult result = connector.fetchMessages(connection, credentials, null, 50);

        assertEquals(1, result.messages().size());
        // Must be the History API baseline, NOT the listing's own "there-is-more-history-after-this"
        // page token - the connector must not keep following that pagination trail.
        assertEquals("h:777000", result.newCursor());
        assertFalse(result.hasMore());
        mockServer.verify();
    }

    @Test
    @DisplayName("Gmail: an 'h:'-prefixed cursor advances to the response's new historyId for the next poll")
    void testHistoryCursorAdvancesToLatestHistoryId() {
        String strHistoryResponse = """
            {
              "history": [],
              "historyId": "999111"
            }
            """;

        mockServer.expect(requestTo(
                        "https://gmail.googleapis.com/gmail/v1/users/me/history?startHistoryId=100&historyTypes=messageAdded&maxResults=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(strHistoryResponse, MediaType.APPLICATION_JSON));

        FetchResult result = connector.fetchMessages(connection, credentials, "h:100", 50);

        assertTrue(result.messages().isEmpty());
        assertEquals("h:999111", result.newCursor());
        mockServer.verify();
    }

    @Test
    @DisplayName("Gmail: Attachments with attachmentId are downloaded from Gmail attachment API and normalized")
    void testFetchMessageDownloadsExternalAttachments() {
        String strMessageId = "msg_with_pdf";
        byte[] expectedPdfBytes = "%PDF-1.4 Mock PO content".getBytes(StandardCharsets.UTF_8);
        String base64UrlData = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedPdfBytes);

        String strMessageResponse = """
            {
              "id": "msg_with_pdf",
              "internalDate": "1700000000000",
              "snippet": "PO attached",
              "payload": {
                "headers": [
                  {"name": "Subject", "value": "Purchase Order PO-900"},
                  {"name": "From", "value": "supplier@test.com"}
                ],
                "parts": [
                  {
                    "mimeType": "text/plain",
                    "body": {"data": "UGxlYXNlIGZpbmQgUE8="}
                  },
                  {
                    "filename": "PO_900.pdf",
                    "mimeType": "application/pdf",
                    "body": {
                      "size": 24,
                      "attachmentId": "att_pdf_12345"
                    }
                  }
                ]
              }
            }
            """;

        String strAttachmentResponse = """
            {
              "size": 24,
              "data": "%s"
            }
            """.formatted(base64UrlData);

        mockServer.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/msg_with_pdf?format=full"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(strMessageResponse, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/msg_with_pdf/attachments/att_pdf_12345"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(strAttachmentResponse, MediaType.APPLICATION_JSON));

        NormalizedEmail email = connector.fetchMessage(connection, credentials, strMessageId);

        assertNotNull(email);
        assertEquals(1, email.attachments().size());
        assertEquals("PO_900.pdf", email.attachments().get(0).fileName());
        assertEquals("application/pdf", email.attachments().get(0).contentType());
        assertEquals(expectedPdfBytes.length, email.attachments().get(0).sizeBytes());
        mockServer.verify();
    }
}
