package com.umis.tenant.dto;

import java.util.UUID;

/**
 * Authentication response payload returning JWT access token and user metadata.
 */
public record AuthResponse(
        String token,
        String tokenType,
        UUID userId,
        String email,
        String fullName,
        String role,
        UUID tenantId,
        String tenantCode,
        String tenantName
) {
    public static AuthResponse of(
            String token,
            UUID userId,
            String email,
            String fullName,
            String role,
            UUID tenantId,
            String tenantCode,
            String tenantName
    ) {
        return new AuthResponse(
                token,
                "Bearer",
                userId,
                email,
                fullName,
                role,
                tenantId,
                tenantCode,
                tenantName
        );
    }
}
