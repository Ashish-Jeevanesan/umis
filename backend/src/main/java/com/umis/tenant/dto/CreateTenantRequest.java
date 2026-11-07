package com.umis.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a new tenant.
 */
public record CreateTenantRequest(
        @NotBlank(message = "Tenant code is required")
        @Size(min = 2, max = 64, message = "Tenant code must be between 2 and 64 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Tenant code can only contain alphanumeric characters, hyphens, and underscores")
        String code,

        @NotBlank(message = "Tenant name is required")
        @Size(min = 2, max = 255, message = "Tenant name must be between 2 and 255 characters")
        String name,

        String adminEmail,
        String adminFullName,
        String adminPassword
) {}
