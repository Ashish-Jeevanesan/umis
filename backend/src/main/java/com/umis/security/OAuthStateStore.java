package com.umis.security;

import com.umis.common.exception.BadRequestException;
import com.umis.mailbox.model.MailboxProvider;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store and validator for OAuth state parameters to prevent CSRF attacks.
 * Binds state tokens to tenant IDs and providers with a 15-minute time-to-live.
 */
@Component
public class OAuthStateStore {

    private static final int STATE_EXPIRY_MINUTES = 15;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, OAuthStateRecord> hmActiveStates = new ConcurrentHashMap<>();

    /**
     * Generates a cryptographically secure OAuth state string bound to a tenant, provider, and redirect URI.
     *
     * @param tenantId UUID of the tenant initiating connection
     * @param provider mailbox provider being connected
     * @param redirectUri redirect URI that will be used for this authorization attempt
     * @return secure state token
     */
    public String generateState(UUID tenantId, MailboxProvider provider, String redirectUri) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String strToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Instant expiresAt = Instant.now().plus(STATE_EXPIRY_MINUTES, ChronoUnit.MINUTES);
        hmActiveStates.put(strToken, new OAuthStateRecord(tenantId, provider, redirectUri, expiresAt));
        cleanupExpiredStates();
        return strToken;
    }

    /**
     * Validates and immediately consumes an OAuth state parameter.
     *
     * @param strState state token returned by provider callback
     * @param expectedTenantId tenant ID expected to own the connection
     * @param expectedProvider mailbox provider being connected
     * @param expectedRedirectUri redirect URI the caller is completing the connection with; must match the
     *                            one supplied when the state was generated, preventing a token issued for one
     *                            redirect target from being replayed against another
     * @throws BadRequestException if state is invalid, expired, or mismatched
     */
    public void validateAndConsume(String strState, UUID expectedTenantId, MailboxProvider expectedProvider, String expectedRedirectUri) {
        if (strState == null || strState.isBlank()) {
            throw new BadRequestException("OAuth state parameter is missing");
        }

        OAuthStateRecord record = hmActiveStates.remove(strState);
        if (record == null) {
            throw new BadRequestException("Invalid or already consumed OAuth state parameter");
        }

        if (record.expiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("OAuth state parameter has expired. Please re-initiate connection");
        }

        if (expectedTenantId != null && !expectedTenantId.equals(record.tenantId())) {
            throw new BadRequestException("OAuth state tenant mismatch");
        }

        if (expectedProvider != null && expectedProvider != record.provider()) {
            throw new BadRequestException("OAuth state provider mismatch");
        }

        if (!record.redirectUri().equals(expectedRedirectUri)) {
            throw new BadRequestException("OAuth state redirect URI mismatch");
        }
    }

    private void cleanupExpiredStates() {
        Instant now = Instant.now();
        hmActiveStates.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record OAuthStateRecord(
            UUID tenantId,
            MailboxProvider provider,
            String redirectUri,
            Instant expiresAt
    ) {}
}
