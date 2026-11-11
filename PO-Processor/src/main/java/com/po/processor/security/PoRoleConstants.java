package com.po.processor.security;

import java.util.ArrayList;
import java.util.List;

/**
 * System and tenant-level authorization roles for the PO Processor engine.
 */
public final class PoRoleConstants {

    private PoRoleConstants() {
        // Constants class
    }

    public static final String PO_VIEWER = "PO_VIEWER";
    public static final String PO_REVIEWER = "PO_REVIEWER";
    public static final String PO_VERIFIER = "PO_VERIFIER";
    public static final String PO_APPROVER = "PO_APPROVER";
    public static final String PO_ADMIN = "PO_ADMIN";

    // UMIS roles recognized and mapped into PO permissions
    public static final String GLOBAL_ADMIN = "GLOBAL_ADMIN";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String TENANT_VIEWER = "TENANT_VIEWER";

    public static final String ROLE_PO_VIEWER = "ROLE_" + PO_VIEWER;
    public static final String ROLE_PO_REVIEWER = "ROLE_" + PO_REVIEWER;
    public static final String ROLE_PO_VERIFIER = "ROLE_" + PO_VERIFIER;
    public static final String ROLE_PO_APPROVER = "ROLE_" + PO_APPROVER;
    public static final String ROLE_PO_ADMIN = "ROLE_" + PO_ADMIN;

    /**
     * Maps an incoming raw role claim (from UMIS or PO token) to appropriate PO authorities.
     *
     * @param strRawRole Raw role string
     * @return List of mapped role names
     */
    public static List<String> mapAuthorities(String strRawRole) {
        List<String> alRoles = new ArrayList<>();
        if (strRawRole == null || strRawRole.isBlank()) {
            alRoles.add(PO_VIEWER);
            return alRoles;
        }

        String strRole = strRawRole.trim().toUpperCase();
        if (strRole.startsWith("ROLE_")) {
            strRole = strRole.substring(5);
        }

        switch (strRole) {
            case GLOBAL_ADMIN -> {
                alRoles.add(GLOBAL_ADMIN);
                alRoles.add(PO_ADMIN);
                alRoles.add(PO_APPROVER);
                alRoles.add(PO_VERIFIER);
                alRoles.add(PO_REVIEWER);
                alRoles.add(PO_VIEWER);
            }
            case TENANT_ADMIN, PO_ADMIN -> {
                alRoles.add(PO_ADMIN);
                alRoles.add(PO_APPROVER);
                alRoles.add(PO_VERIFIER);
                alRoles.add(PO_REVIEWER);
                alRoles.add(PO_VIEWER);
            }
            case PO_APPROVER -> {
                alRoles.add(PO_APPROVER);
                alRoles.add(PO_VERIFIER);
                alRoles.add(PO_REVIEWER);
                alRoles.add(PO_VIEWER);
            }
            case PO_VERIFIER -> {
                alRoles.add(PO_VERIFIER);
                alRoles.add(PO_REVIEWER);
                alRoles.add(PO_VIEWER);
            }
            case PO_REVIEWER -> {
                alRoles.add(PO_REVIEWER);
                alRoles.add(PO_VIEWER);
            }
            default -> alRoles.add(PO_VIEWER);
        }

        return alRoles;
    }
}
