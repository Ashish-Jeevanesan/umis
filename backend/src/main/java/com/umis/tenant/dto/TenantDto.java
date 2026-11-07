package com.umis.tenant.dto;

import com.umis.tenant.model.Tenant;
import com.umis.tenant.model.TenantStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object representing organization details.
 */
public record TenantDto(
        UUID id,
        String code,
        String name,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static TenantDto from(Tenant tenant) {
        return new TenantDto(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
