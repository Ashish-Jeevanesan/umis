package com.umis.security;

import com.umis.common.exception.BadRequestException;
import com.umis.common.exception.UnauthorizedException;
import com.umis.config.UmisProperties;
import com.umis.email.model.EmailAttachment;
import com.umis.email.model.EmailMessage;
import com.umis.email.model.IngestionStatus;
import com.umis.email.repository.EmailAttachmentRepository;
import com.umis.email.repository.EmailMessageRepository;
import com.umis.email.service.EmailQueryService;
import com.umis.email.service.EmailStorageService;
import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.SyncStrategy;
import com.umis.mailbox.repository.MailboxConnectionRepository;
import com.umis.mailbox.repository.MailboxSubscriptionRepository;
import com.umis.tenant.model.Tenant;
import com.umis.tenant.repository.TenantRepository;
import com.umis.tenant.service.TenantService;
import com.umis.webhook.model.WebhookEvent;
import com.umis.webhook.repository.WebhookEventRepository;
import com.umis.webhook.service.WebhookProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SecurityAndIsolationTest {

    @Autowired
    private OAuthStateStore oauthStateStore;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private MailboxConnectionRepository mailboxRepository;

    @Autowired
    private EmailMessageRepository messageRepository;

    @Autowired
    private EmailAttachmentRepository attachmentRepository;

    @Autowired
    private EmailQueryService emailQueryService;

    @Autowired
    private WebhookProcessingService webhookProcessingService;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    private Tenant tenantA;
    private Tenant tenantB;
    private EmailMessage emailMessageA;
    private EmailAttachment attachmentA;

    @BeforeEach
    void setUp() {
        tenantA = tenantRepository.save(new Tenant("ORG-ALPHA", "Tenant Alpha"));
        tenantB = tenantRepository.save(new Tenant("ORG-BETA", "Tenant Beta"));

        MailboxConnection mailboxA = mailboxRepository.save(new MailboxConnection(
                tenantA.getId(), "orders@alpha.com", "Alpha Orders",
                MailboxProvider.MICROSOFT_365, SyncStrategy.POLLING));

        emailMessageA = new EmailMessage(
                tenantA.getId(), mailboxA, MailboxProvider.MICROSOFT_365,
                "msg_alpha_001", "sender@client.com", "Client Sender",
                "PO 12345", "Snippet text", "hash123", Instant.now());
        emailMessageA.setIngestionStatus(IngestionStatus.INGESTED);
        messageRepository.save(emailMessageA);

        attachmentA = new EmailAttachment(
                emailMessageA, tenantA.getId(), "secret_contract.pdf", "application/pdf",
                1024L, "hash_pdf", "attachments/alpha/secret_contract.pdf");
        attachmentRepository.save(attachmentA);
    }

    private void authenticateTenant(UUID tenantId, String strRole) {
        TenantContext.setTenantId(tenantId);
        String roleName = strRole.startsWith("ROLE_") ? strRole.substring(5) : strRole;
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), tenantId, "user@" + tenantId + ".com", "pass", roleName, true);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + roleName)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Prevent cross-tenant attachment download via guessed UUIDs")
    void testCrossTenantAttachmentAccessRejected() {
        // Authenticate as Tenant B
        authenticateTenant(tenantB.getId(), RoleConstants.TENANT_ADMIN);

        // Tenant B attempts to download Tenant A's attachment using emailId and attachmentId
        assertThrows(UnauthorizedException.class, () ->
                emailQueryService.getAttachmentDownload(emailMessageA.getId(), attachmentA.getId())
        );

        // Tenant B attempts to list attachments of Tenant A's email
        assertThrows(UnauthorizedException.class, () ->
                emailQueryService.listAttachments(emailMessageA.getId())
        );
    }

    @Test
    @DisplayName("OAuth state store enforces single-use consumption and expiration")
    void testOAuthStateStoreSingleUse() {
        UUID testTenantId = UUID.randomUUID();
        String redirectUri = "http://localhost:3000/";
        String strState = oauthStateStore.generateState(testTenantId, MailboxProvider.MICROSOFT_365, redirectUri);
        assertNotNull(strState);

        // First validation must succeed
        oauthStateStore.validateAndConsume(strState, testTenantId, MailboxProvider.MICROSOFT_365, redirectUri);

        // Second validation with the same token must fail (replay attack prevention)
        assertThrows(BadRequestException.class, () ->
                oauthStateStore.validateAndConsume(strState, testTenantId, MailboxProvider.MICROSOFT_365, redirectUri)
        );
    }

    @Test
    @DisplayName("OAuth state validation rejects mismatched tenant ID")
    void testOAuthStateMismatchedTenant() {
        UUID tenantOne = UUID.randomUUID();
        UUID tenantTwo = UUID.randomUUID();
        String redirectUri = "http://localhost:3000/";
        String strState = oauthStateStore.generateState(tenantOne, MailboxProvider.GMAIL, redirectUri);

        assertThrows(BadRequestException.class, () ->
                oauthStateStore.validateAndConsume(strState, tenantTwo, MailboxProvider.GMAIL, redirectUri)
        );
    }

    @Test
    @DisplayName("OAuth state validation rejects a redirect URI different from the one used at initiation")
    void testOAuthStateMismatchedRedirectUri() {
        UUID testTenantId = UUID.randomUUID();
        String strState = oauthStateStore.generateState(testTenantId, MailboxProvider.GMAIL, "http://localhost:3000/");

        assertThrows(BadRequestException.class, () ->
                oauthStateStore.validateAndConsume(strState, testTenantId, MailboxProvider.GMAIL, "http://attacker.example/")
        );
    }

    @Test
    @DisplayName("Envelope encryption key rotation decrypts with previous key")
    void testEncryptionKeyRotationFallback() {
        String strOriginalKey = "original-secret-key-32-characters";
        String strRotatedKey = "rotated-new-secret-key-32-chars!!";

        UmisProperties propsOld = new UmisProperties();
        propsOld.getSecurity().setEncryptionKey(strOriginalKey);
        EncryptionService oldService = new EncryptionService(propsOld);

        String strCiphertext = oldService.encrypt("SuperSecretToken123");
        assertNotNull(strCiphertext);

        // New service with rotated key and previous key configured
        UmisProperties propsNew = new UmisProperties();
        propsNew.getSecurity().setEncryptionKey(strRotatedKey);
        propsNew.getSecurity().setPreviousEncryptionKey(strOriginalKey);
        EncryptionService newService = new EncryptionService(propsNew);

        // Must successfully decrypt ciphertext created with the old key
        String strDecrypted = newService.decrypt(strCiphertext);
        assertEquals("SuperSecretToken123", strDecrypted);
    }

    @Test
    @DisplayName("Webhook processing rejects unauthenticated notifications with mismatched clientState")
    void testWebhookRejectsForgedClientState() {
        String strFakePayload = """
            {
              "value": [
                {
                  "subscriptionId": "sub_fake_123",
                  "clientState": "forged_secret_token",
                  "resource": "Users/unknown/Messages"
                }
              ]
            }
            """;

        webhookProcessingService.processMicrosoftWebhook(strFakePayload, "Host: api.umis.local");

        List<WebhookEvent> alEvents = webhookEventRepository.findAll();
        boolean hasUnknownSub = alEvents.stream().anyMatch(evt ->
                "UNKNOWN_SUBSCRIPTION".equals(evt.getStatus()) || "FAILED".equals(evt.getStatus()));
        assertTrue(hasUnknownSub, "Unregistered webhook subscription must be rejected");
    }
}
