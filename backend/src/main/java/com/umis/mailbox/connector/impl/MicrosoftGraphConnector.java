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
 * Microsoft 365 / Outlook connector integrating with Microsoft Graph REST API.
 */
@Component
public class MicrosoftGraphConnector implements MailboxConnector {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftGraphConnector.class);
    private static final String GRAPH_API_BASE = "https://graph.microsoft.com/v1.0";
    private static final String DEFAULT_SCOPES = "offline_access https://graph.microsoft.com/Mail.Read";

    @Value("${AZURE_CLIENT_ID:mock-azure-client-id}")
    private String clientId;

    @Value("${AZURE_CLIENT_SECRET:mock-azure-client-secret}")
    private String clientSecret;

    @Value("${AZURE_TENANT_ID:common}")
    private String tenantId;

    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public MicrosoftGraphConnector(EncryptionService encryptionService, ObjectMapper objectMapper) {
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public MailboxProvider getProvider() {
        return MailboxProvider.MICROSOFT_365;
    }

    @Override
    public ProviderCapability getCapability() {
        return new ProviderCapability(
                MailboxProvider.MICROSOFT_365,
                "Microsoft 365 / Outlook",
                "OAUTH2",
                "PKCE_AUTHORIZATION_CODE",
                List.of("READ_MESSAGES", "ATTACHMENTS", "DELTA_SYNC", "WEBHOOKS", "HISTORICAL_SYNC"),
                true,
                true
        );
    }

    @Override
    public ProviderConnectionInit initiateConnection(String redirectUri, String state) {
        String codeVerifier = UUID.randomUUID().toString().replace("-", "");
        String authUrl = String.format(
                "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize?client_id=%s&response_type=code" +
                        "&redirect_uri=%s&response_mode=query&scope=%s&state=%s",
                tenantId,
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
            String tokenUrl = String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/token", tenantId);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("code", authorizationCode);
            body.add("redirect_uri", redirectUri);
            body.add("grant_type", "authorization_code");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            String accessToken = root.path("access_token").asText();
            String refreshToken = root.path("refresh_token").asText();
            int expiresIn = root.path("expires_in").asInt(3600);

            // Fetch user profile from Graph /me
            HttpHeaders authHeader = new HttpHeaders();
            authHeader.setBearerAuth(accessToken);
            ResponseEntity<String> meResponse = restTemplate.exchange(
                    GRAPH_API_BASE + "/me", HttpMethod.GET, new HttpEntity<>(authHeader), String.class);
            JsonNode meNode = objectMapper.readTree(meResponse.getBody());
            String email = meNode.path("mail").asText(meNode.path("userPrincipalName").asText());
            String displayName = meNode.path("displayName").asText(email);

            return ConnectionResult.ok(
                    email, displayName, accessToken, refreshToken,
                    Instant.now().plus(expiresIn, ChronoUnit.SECONDS), DEFAULT_SCOPES);
        } catch (Exception exception) {
            log.warn("Microsoft Graph token exchange failed: {}", exception.getMessage());
            // If live credentials not provided, throw informative integration exception
            throw new MailboxIntegrationException("Microsoft OAuth token exchange failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public boolean refreshCredentials(MailboxCredentials credentials) {
        try {
            String refreshToken = encryptionService.decrypt(credentials.getEncryptedRefreshToken());
            if (refreshToken == null) {
                return false;
            }
            String tokenUrl = String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/token", tenantId);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("refresh_token", refreshToken);
            body.add("grant_type", "refresh_token");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            credentials.setEncryptedAccessToken(encryptionService.encrypt(root.path("access_token").asText()));
            if (root.has("refresh_token")) {
                credentials.setEncryptedRefreshToken(encryptionService.encrypt(root.path("refresh_token").asText()));
            }
            credentials.setTokenExpiresAt(Instant.now().plus(root.path("expires_in").asInt(3600), ChronoUnit.SECONDS));
            return true;
        } catch (Exception exception) {
            log.error("Failed to refresh Microsoft Graph token: {}", exception.getMessage());
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
                    GRAPH_API_BASE + "/me/mailFolders/inbox", HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception exception) {
            log.warn("Graph health check failed for {}: {}", connection.getEmailAddress(), exception.getMessage());
            return false;
        }
    }

    @Override
    public FetchResult fetchMessages(MailboxConnection connection, MailboxCredentials credentials, String cursor, int maxResults) {
        try {
            String token = encryptionService.decrypt(credentials.getEncryptedAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            String url = (cursor != null && !cursor.isBlank())
                    ? cursor
                    : GRAPH_API_BASE + "/me/mailFolders/inbox/messages/delta?$top=" + (maxResults > 0 ? maxResults : 50);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode valueArray = root.path("value");

            List<NormalizedEmail> emails = new ArrayList<>();
            for (JsonNode node : valueArray) {
                emails.add(mapGraphMessageToNormalized(connection, node, credentials));
            }

            String nextCursor = root.path("@odata.deltaLink").asText(root.path("@odata.nextLink").asText(null));
            boolean hasMore = root.hasNonNull("@odata.nextLink");
            return FetchResult.of(emails, nextCursor, hasMore);
        } catch (Exception exception) {
            String strError = exception.getMessage() != null ? exception.getMessage().toLowerCase() : "";
            if (strError.contains("resyncrequired") || strError.contains("410")
                    || (strError.contains("delta") && strError.contains("expired"))) {
                throw new MailboxIntegrationException(
                        "Microsoft Graph delta cursor expired: " + exception.getMessage(), exception);
            }
            if (strError.contains("429") || strError.contains("too many requests")) {
                throw new MailboxIntegrationException(
                        "Microsoft Graph rate limit exceeded (429): " + exception.getMessage(), exception);
            }
            throw new MailboxIntegrationException("Graph fetch messages failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public NormalizedEmail fetchMessage(MailboxConnection connection, MailboxCredentials credentials, String providerMessageId) {
        try {
            String token = encryptionService.decrypt(credentials.getEncryptedAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            String url = GRAPH_API_BASE + "/me/messages/" + providerMessageId;
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode node = objectMapper.readTree(response.getBody());
            return mapGraphMessageToNormalized(connection, node, credentials);
        } catch (Exception exception) {
            throw new MailboxIntegrationException("Graph fetch message failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public List<NormalizedAttachment> fetchAttachments(MailboxConnection connection, MailboxCredentials credentials, String providerMessageId) {
        try {
            String token = encryptionService.decrypt(credentials.getEncryptedAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            String url = GRAPH_API_BASE + "/me/messages/" + providerMessageId + "/attachments";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            List<NormalizedAttachment> attachments = new ArrayList<>();
            for (JsonNode attNode : root.path("value")) {
                String name = attNode.path("name").asText("attachment.bin");
                String ctype = attNode.path("contentType").asText("application/octet-stream");
                byte[] bytes = Base64.getDecoder().decode(attNode.path("contentBytes").asText(""));
                String hash = HashUtils.sha256Hex(bytes);
                attachments.add(NormalizedAttachment.of(name, ctype, bytes.length, hash, bytes));
            }
            return attachments;
        } catch (Exception exception) {
            log.error("Failed to fetch Graph attachments for {}: {}", providerMessageId, exception.getMessage());
            return List.of();
        }
    }

    @Override
    public String startMonitoring(MailboxConnection connection, MailboxCredentials credentials, String webhookUrl) {
        try {
            String token = encryptionService.decrypt(credentials.getEncryptedAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestBody = objectMapper.writeValueAsString(new GraphSubscriptionRequest(
                    "created",
                    GRAPH_API_BASE + "/me/mailFolders('inbox')/messages",
                    webhookUrl,
                    Instant.now().plus(4230, ChronoUnit.MINUTES).toString(), // ~3 days max
                    connection.getId().toString()
            ));

            ResponseEntity<String> response = restTemplate.postForEntity(
                    GRAPH_API_BASE + "/subscriptions", new HttpEntity<>(requestBody, headers), String.class);
            JsonNode node = objectMapper.readTree(response.getBody());
            return node.path("id").asText();
        } catch (Exception exception) {
            log.error("Failed to establish Graph subscription: {}", exception.getMessage());
            return null;
        }
    }

    @Override
    public boolean stopMonitoring(MailboxConnection connection, MailboxCredentials credentials, String subscriptionId) {
        try {
            String token = encryptionService.decrypt(credentials.getEncryptedAccessToken());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            restTemplate.exchange(GRAPH_API_BASE + "/subscriptions/" + subscriptionId, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            return true;
        } catch (Exception exception) {
            log.warn("Failed to delete Graph subscription {}: {}", subscriptionId, exception.getMessage());
            return false;
        }
    }

    @Override
    public void disconnect(MailboxConnection connection, MailboxCredentials credentials) {
        log.info("Disconnected Microsoft Graph connection for {}", connection.getEmailAddress());
    }

    private NormalizedEmail mapGraphMessageToNormalized(MailboxConnection connection, JsonNode node, MailboxCredentials credentials) {
        String msgId = node.path("id").asText();
        String subject = node.path("subject").asText("(No Subject)");
        String sender = node.path("from").path("emailAddress").path("address").asText("unknown@unknown.com");
        String senderName = node.path("from").path("emailAddress").path("name").asText(sender);
        String bodyText = node.path("body").path("content").asText("");
        Instant receivedAt = Instant.parse(node.path("receivedDateTime").asText(Instant.now().toString()));
        boolean hasAtt = node.path("hasAttachments").asBoolean(false);
        List<NormalizedAttachment> attachments = hasAtt ? fetchAttachments(connection, credentials, msgId) : List.of();
        String hash = HashUtils.sha256Hex(connection.getId() + ":" + msgId + ":" + subject);

        return NormalizedEmail.create(
                connection.getTenantId(), connection.getId(), MailboxProvider.MICROSOFT_365,
                msgId, sender, senderName, List.of(connection.getEmailAddress()),
                subject, bodyText, bodyText, receivedAt, attachments, hash);
    }

    private record GraphSubscriptionRequest(
            String changeType,
            String resource,
            String notificationUrl,
            String expirationDateTime,
            String clientState
    ) {}
}
