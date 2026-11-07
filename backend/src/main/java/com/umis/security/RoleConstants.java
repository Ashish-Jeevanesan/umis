package com.umis.security;

/**
 * System and tenant-level authorization roles.
 */
public final class RoleConstants {

    private RoleConstants() {
        // Constants class
    }

    public static final String GLOBAL_ADMIN = "GLOBAL_ADMIN";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String TENANT_VIEWER = "TENANT_VIEWER";
    public static final String SERVICE_CLIENT = "SERVICE_CLIENT";

    public static final String ROLE_GLOBAL_ADMIN = "ROLE_" + GLOBAL_ADMIN;
    public static final String ROLE_TENANT_ADMIN = "ROLE_" + TENANT_ADMIN;
    public static final String ROLE_TENANT_VIEWER = "ROLE_" + TENANT_VIEWER;
    public static final String ROLE_SERVICE_CLIENT = "ROLE_" + SERVICE_CLIENT;

    public static final String HAS_GLOBAL_ADMIN = "hasRole('" + GLOBAL_ADMIN + "')";
    public static final String HAS_TENANT_ADMIN = "hasAnyRole('" + GLOBAL_ADMIN + "', '" + TENANT_ADMIN + "')";
    public static final String HAS_TENANT_ACCESS = "hasAnyRole('" + GLOBAL_ADMIN + "', '" + TENANT_ADMIN + "', '" + TENANT_VIEWER + "')";
}
