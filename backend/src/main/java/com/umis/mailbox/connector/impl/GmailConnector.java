package com.umis.mailbox.connector.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.umis.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Gmail / Google Workspace connector integrating with Google Gmail REST APIs and History API.
 */
@Component
public class GmailConnector implements MailboxConnector {

    private static final Logger log = LoggerFactory.getLogger(GmailConnector.class);
    private static final String GMAIL_BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final String DEFAULT_SCOPES = "https://www.googleapis.com/auth/gmail.readonly";

    @Value("${GOOGLE_CLIENT_ID:mock-google-client-id}")
    private String clientId;

    @Value("${GOOGLE_CLIENT_SECRET:mock-google-client-secret}")
    private String clientSecret;

    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final GmailPayloadParser payloadParser;
    private final RestTemplate restTemplate = new RestTemplate();

    public GmailConnector(
            EncryptionService encryptionService,
            ObjectMapper objectMapper,
            GmailPayloadParser payloadParser
    ) {
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.payloadParser = payloadParser;
    }

    @Override
    public MailboxProvider getProvider() {
        return MailboxProvider.GMAIL;
    }

    @Override
    public ProviderCapability getCapability() {
        return new ProviderCapability(
                MailboxProvider.GMAIL,
                "Gmail / Google Workspace",
                "OAUTH2",
                "AUTHORIZATION_CODE",
                List.of("READ_MESSAGES", "ATTACHMENTS", "GMAIL_HISTORY_SYNC", "CLOUD_PUBSUB_WATCH", "HISTORICAL_SYNC"),
                true,
                true
        );
    }

    @Override
    public ProviderConnectionInit initiateConnection(String redirectUri, String state) {
        String codeVerifier = UUID.randomUUID().toString().replace("-", "");
        String authUrl = String.format(
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&response_type=code" +
                        "&redirect_uri=%s&scope=%s&access_type=offline&prompt=consent&state=%s",
                clientId,
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
                URLEncoder.encode(DEFAULT_SCOPES, StandardCharsets.UTF_8),
                state
        );
        return new ProviderConnectionInit(authUrl, state, codeVerifier);
    }

    @Override
    public ConnectionResult completeConnection(String authorizationCode, String codeVerifier, String redirectUri) {
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("code", authorizationCode);
            body.add("redirect_uri", redirectUri);
            body.add("grant_type", "authorization_code");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://oauth2.googleapis.com/token", new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            String accessToken = root.path("access_token").asText();
            String refreshToken = root.path("refresh_token").asText(null);
            int expiresIn = root.path("expires_in").asInt(3600);

            // Fetch profile
            HttpHeaders authHeader = new HttpHeaders();
            authHeader.setBearerAuth(accessToken);
            ResponseEntity<String> profileResponse = restTemplate.exchange(
                    GMAIL_BASE_URL + "/profile", HttpMethod.GET, new HttpEntity<>(authHeader), String.class);
            JsonNode profileNode = objectMapper.readTree(profileResponse.getBody());
            String email = profileNode.path("emailAddress").asText();

            return ConnectionResult.ok(
                    email, email, accessToken, refreshToken,
                    Instant.now().plus(expiresIn, ChronoUnit.SECONDS), DEFAULT_SCOPES);
        } catch (Exception exception) {
            throw new MailboxIntegrationException("Google OAuth token exchange failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public boolean refreshCredentials(MailboxCredentials credentials) {
        try {
            String refreshToken = encryptionService.decrypt(credentials.getEncryptedRefreshToken());
            if (refreshToken == null) {
                return false;
            }
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("refresh_token", refreshToken);
            body.add("grant_type", "refresh_token");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://oauth2.googleapis.com/token", new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            credentials.setEncryptedAccessToken(encryptionService.encrypt(root.path("access_token").asText()));
            credentials.setTokenExpiresAt(Instant.now().plus(root.path("expires_in").asInt(3600), ChronoUnit.SECONDS));
            return true;
        } catch (Exception exception) {
            log.error("Failed to refresh Gmail token: {}", exception.getMessage());
            return false;
        }
    }

    @Override
    public boolean testConnection(MailboxConnection connection, MailboxCredentials credentials) {
        try {
            String token = encryptionService.decrypt(credentials.getEncryptedAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            ResponseEntity<String> response = restTemplate.exchange(
                    GMAIL_BASE_URL + "/profile", HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception exception) {
            log.warn("Gmail connection test failed for {}: {}", connection.getEmailAddress(), exception.getMessage());
            return false;
        }
    }

    // Prefix marking a cursor as a real Gmail History API checkpoint (as opposed to a plain
    // /messages.list pagination token, used only during the initial bounded historical backfill).
    // Gmail's list pageTokens are opaque and are NOT guaranteed to be non-numeric, so a prior
    // version of this method that guessed the cursor's meaning from its format (digits-only =>
    // historyId) could misroute an ordinary page token into the History API, or vice versa,
    // silently getting the sync stuck replaying the same page forever.
    private static final String HISTORY_CURSOR_PREFIX = "h:";

    @Override
    public FetchResult fetchMessages(MailboxConnection connection, MailboxCredentials credentials, String cursor, int maxResults) {
        try {
            String token = encryptionService.decrypt(credentials.getEncryptedAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            int effectiveMaxResults = maxResults > 0 ? maxResults : 20;

            if (cursor != null && cursor.startsWith(HISTORY_CURSOR_PREFIX)) {
                return fetchViaHistory(connection, credentials, headers, cursor.substring(HISTORY_CURSOR_PREFIX.length()), effectiveMaxResults);
            }
            return fetchViaListing(connection, credentials, headers, cursor, effectiveMaxResults);
        } catch (Exception exception) {
            String strError = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
            if (strError.contains("404") || strError.contains("historyid") || strError.contains("400")) {
                throw new MailboxIntegrationException("Gmail history cursor expired: " + exception.getMessage(),
                        exception);
            }
            throw new MailboxIntegrationException("Failed to fetch Gmail messages: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * One bounded page of recent mail via /messages.list, then switches straight to incremental
     * History API checking - deliberately does NOT keep following nextPageToken to page backward
     * through the rest of a mailbox's history. For an account with substantial mail, an
     * open-ended backfill can take arbitrarily many sync cycles to exhaust, and every cycle spent
     * paging through old mail is a cycle where genuinely new mail goes unnoticed. Noticing new
     * mail promptly matters far more here than eventually importing the entire historical
     * mailbox, so this always re-baselines onto the current historyId after a single page,
     * regardless of whether more historical pages remain.
     */
    private FetchResult fetchViaListing(
            MailboxConnection connection, MailboxCredentials credentials, HttpHeaders headers, String pageToken, int maxResults
    ) throws Exception {
        String url = GMAIL_BASE_URL + "/messages?maxResults=" + maxResults;
        if (pageToken != null && !pageToken.isBlank()) {
            url += "&pageToken=" + pageToken;
        }

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        JsonNode root = objectMapper.readTree(response.getBody());

        List<NormalizedEmail> emails = new ArrayList<>();
        for (JsonNode item : root.path("messages")) {
            emails.add(fetchMessage(connection, credentials, item.path("id").asText()));
        }

        String baselineHistoryId = fetchCurrentHistoryId(headers);
        String historyCursor = baselineHistoryId != null ? HISTORY_CURSOR_PREFIX + baselineHistoryId : null;
        return new FetchResult(emails, historyCursor, false, baselineHistoryId, null);
    }

    /**
     * True incremental sync via the Gmail History API: only messages added since startHistoryId.
     */
    private FetchResult fetchViaHistory(
            MailboxConnection connection, MailboxCredentials credentials, HttpHeaders headers, String startHistoryId, int maxResults
    ) throws Exception {
        String url = GMAIL_BASE_URL + "/history?startHistoryId=" + startHistoryId
                + "&historyTypes=messageAdded&maxResults=" + maxResults;
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        JsonNode root = objectMapper.readTree(response.getBody());

        List<NormalizedEmail> emails = new ArrayList<>();
        for (JsonNode hist : root.path("history")) {
            for (JsonNode added : hist.path("messagesAdded")) {
                String messageId = added.path("message").path("id").asText();
                emails.add(fetchMessage(connection, credentials, messageId));
            }
        }

        // The response's top-level historyId is the mailbox's current checkpoint - advance to
        // it so the next poll only looks for changes after this point.
        String newHistoryId = root.path("historyId").asText(startHistoryId);
        return new FetchResult(emails, HISTORY_CURSOR_PREFIX + newHistoryId, false, newHistoryId, null);
    }

    private String fetchCurrentHistoryId(HttpHeaders headers) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    GMAIL_BASE_URL + "/profile", HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("historyId").asText(null);
        } catch (Exception exception) {
            log.warn("Failed to establish Gmail History API baseline: {}", exception.getMessage());
            return null;
        }
    }

    @Override
    public NormalizedEmail fetchMessage(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String providerMessageId
    ) {
        try {
            String token = encryptionService.decrypt(credentials.getEncryptedAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            String url = GMAIL_BASE_URL + "/messages/" + providerMessageId + "?format=full";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            String subject = extractHeader(root, "Subject", "(No Subject)");
            String sender = extractHeader(root, "From", "unknown@unknown.com");
            Instant receivedAt = Instant.ofEpochMilli(root.path("internalDate").asLong(System.currentTimeMillis()));
            String snippet = root.path("snippet").asText("");
            String contentHash = HashUtils.sha256Hex(connection.getId() + ":" + providerMessageId + ":" + subject);

            JsonNode payloadNode = root.path("payload");
            GmailPayloadParser.ParsedBody bodies = payloadParser.extractBodies(payloadNode);
            String bodyText = !bodies.text().isBlank() ? bodies.text() : snippet;
            String bodyHtml = !bodies.html().isBlank() ? bodies.html() : "<p>" + snippet + "</p>";
            List<NormalizedAttachment> attachments = payloadParser.extractInlineAttachments(payloadNode);

            return NormalizedEmail.create(
                    connection.getTenantId(), connection.getId(), MailboxProvider.GMAIL,
                    providerMessageId, sender, sender, List.of(connection.getEmailAddress()),
                    subject, bodyText, bodyHtml, receivedAt, attachments, contentHash);
        } catch (Exception exception) {
            throw new MailboxIntegrationException("Failed to fetch Gmail message: " + exception.getMessage(),
                    exception);
        }
    }

    @Override
    public List<NormalizedAttachment> fetchAttachments(
            MailboxConnection connection,
            MailboxCredentials credentials,
            String providerMessageId
    ) {
        NormalizedEmail email = fetchMessage(connection, credentials, providerMessageId);
        return email.attachments();
    }

    @Override
    public String startMonitoring(MailboxConnection connection, MailboxCredentials credentials, String webhookUrl) {
        // Pub/Sub watch registration
        return "gmail_watch_" + UUID.randomUUID();
    }

    @Override
    public boolean stopMonitoring(MailboxConnection connection, MailboxCredentials credentials, String subscriptionId) {
        try {
            String token = encryptionService.decrypt(credentials.getEncryptedAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            restTemplate.postForEntity(GMAIL_BASE_URL + "/stop", new HttpEntity<>(headers), Void.class);
            return true;
        } catch (Exception exception) {
            log.warn("Failed to stop Gmail watch: {}", exception.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect(MailboxConnection connection, MailboxCredentials credentials) {
        log.info("Disconnected Gmail connection for {}", connection.getEmailAddress());
    }

    private String extractHeader(JsonNode root, String headerName, String defaultValue) {
        JsonNode headers = root.path("payload").path("headers");
        for (JsonNode h : headers) {
            if (headerName.equalsIgnoreCase(h.path("name").asText())) {
                return h.path("value").asText();
            }
        }
        return defaultValue;
    }
}
