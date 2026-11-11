package com.po.processor.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Validates HMAC-SHA256 JWT tokens and parses authenticated user and tenant identity.
 */
@Component
public class PoJwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(PoJwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long expirationMs;

    public PoJwtTokenProvider(
            @Value("${po.security.jwt.secret:${umis.security.jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}}")
            String strSecret,
            @Value("${po.security.jwt.expiration-ms:86400000}")
            long expirationMs
    ) {
        byte[] keyBytes = strSecret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Verifies that the provided token is signed by the trusted key and not expired.
     *
     * @param strToken JWT bearer token string
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String strToken) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(strToken);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Invalid or expired JWT token: {}", exception.getMessage());
            return false;
        }
    }

    /**
     * Extracts claims and constructs the authenticated PoUserPrincipal.
     *
     * @param strToken Validated JWT token
     * @return PoUserPrincipal with verified user and tenant claims
     */
    public PoUserPrincipal parsePrincipal(String strToken) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(strToken)
                .getPayload();

        String strEmail = claims.getSubject();
        String strUserId = claims.get("userId", String.class);
        String strTenantId = claims.get("tenantId", String.class);
        String strRole = claims.get("role", String.class);

        UUID userId = (strUserId != null && !strUserId.isBlank()) ? UUID.fromString(strUserId) : UUID.randomUUID();
        UUID tenantId = (strTenantId != null && !strTenantId.isBlank()) ? UUID.fromString(strTenantId) : null;

        List<String> alRoles = PoRoleConstants.mapAuthorities(strRole);
        return new PoUserPrincipal(userId, tenantId, strEmail, alRoles);
    }

    /**
     * Utility to generate a signed JWT for testing and service communication.
     *
     * @param userId User identifier
     * @param tenantId Tenant identifier
     * @param strEmail User email / subject
     * @param strRole Primary role
     * @param expiryMillis Expiration duration in milliseconds
     * @return Signed JWT compact string
     */
    public String generateToken(UUID userId, UUID tenantId, String strEmail, String strRole, long expiryMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMillis);

        return Jwts.builder()
                .subject(strEmail)
                .claim("userId", userId != null ? userId.toString() : null)
                .claim("tenantId", tenantId != null ? tenantId.toString() : null)
                .claim("role", strRole)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }
}
