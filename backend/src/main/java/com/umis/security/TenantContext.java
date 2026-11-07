package com.umis.security;

import java.util.UUID;

/**
 * Thread-local context holder for the active tenant during request execution.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Context utility class
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
