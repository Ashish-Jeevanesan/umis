package com.umis.security;

import com.umis.config.UmisProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Utility for generating, signing, and parsing JWT authorization tokens.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(UmisProperties properties) {
        byte[] keyBytes = properties.getSecurity().getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = properties.getSecurity().getJwt().getExpirationMs();
    }

    public String generateToken(UserPrincipal principal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                .orElse("TENANT_VIEWER");

        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("userId", principal.getUserId() != null ? principal.getUserId().toString() : null)
                .claim("tenantId", principal.getTenantId() != null ? principal.getTenantId().toString() : null)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Invalid JWT token: {}", exception.getMessage());
            return false;
        }
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserIdFromToken(String token) {
        String userIdStr = getClaimsFromToken(token).get("userId", String.class);
        return userIdStr != null ? UUID.fromString(userIdStr) : null;
    }

    public UUID getTenantIdFromToken(String token) {
        String tenantIdStr = getClaimsFromToken(token).get("tenantId", String.class);
        return tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
    }

    public String getRoleFromToken(String token) {
        return getClaimsFromToken(token).get("role", String.class);
    }
}
