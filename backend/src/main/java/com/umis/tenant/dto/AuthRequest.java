package com.umis.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Authentication request payload.
 */
public record AuthRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        String password,

        String tenantCode // Optional: if user belongs to multiple tenants or specifies tenant
) {}
