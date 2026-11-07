package com.umis.tenant.dto;

import jakarta.validation.constraints.Size;

/**
 * Payload for updating tenant details.
 */
public record UpdateTenantRequest(
        @Size(min = 2, max = 255, message = "Tenant name must be between 2 and 255 characters")
        String name
) {}
