package com.umis.tenant;

import com.umis.common.exception.UnauthorizedException;
import com.umis.mailbox.dto.ConnectMailboxRequest;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.service.MailboxService;
import com.umis.security.RoleConstants;
import com.umis.security.TenantContext;
import com.umis.security.UserPrincipal;
import com.umis.tenant.dto.CreateTenantRequest;
import com.umis.tenant.dto.TenantDto;
import com.umis.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TenantIsolationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private MailboxService mailboxService;

    private TenantDto tenantA;
    private TenantDto tenantB;

    @BeforeEach
    void setUp() {
        setSecurityContext(UUID.randomUUID(), null, RoleConstants.GLOBAL_ADMIN);

        tenantA = tenantService.createTenant(new CreateTenantRequest(
                "org-alpha-" + UUID.randomUUID().toString().substring(0, 8),
                "Alpha Corp",
                "admin@alpha.com",
                "Admin Alpha",
                "Pass@123"
        ));

        tenantB = tenantService.createTenant(new CreateTenantRequest(
                "org-beta-" + UUID.randomUUID().toString().substring(0, 8),
                "Beta Corp",
                "admin@beta.com",
                "Admin Beta",
                "Pass@123"
        ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A administrator CANNOT access Tenant B data")
    void testTenantIsolationCrossAccessForbidden() {
        // Authenticate as Tenant A Admin
        setSecurityContext(UUID.randomUUID(), tenantA.id(), RoleConstants.TENANT_ADMIN);

        // Accessing Tenant A data succeeds
        assertDoesNotThrow(() -> tenantService.getTenantById(tenantA.id()));

        // Accessing Tenant B data MUST throw UnauthorizedException
        assertThrows(UnauthorizedException.class, () -> tenantService.getTenantById(tenantB.id()));
    }

    @Test
    @DisplayName("Tenant A cannot connect a mailbox for Tenant B")
    void testTenantCannotConnectMailboxForAnotherTenant() {
        // Authenticate as Tenant A
        setSecurityContext(UUID.randomUUID(), tenantA.id(), RoleConstants.TENANT_ADMIN);

        // Attempting to connect a mailbox for Tenant B must be rejected
        ConnectMailboxRequest maliciousRequest = new ConnectMailboxRequest(
                tenantB.id(),
                MailboxProvider.MOCK,
                "inbox@tenant-b.com",
                "Infiltrated Mailbox",
                null,
                null,
                "auth_code",
                "verifier",
                "http://localhost/callback",
                "test_state",
                null, null, null, null
        );

        assertThrows(UnauthorizedException.class, () -> mailboxService.connectMailbox(maliciousRequest));
    }

    @Test
    @DisplayName("Global Administrator CAN access any tenant data")
    void testGlobalAdminCanAccessAllTenants() {
        // Authenticate as Global Admin
        setSecurityContext(UUID.randomUUID(), null, RoleConstants.GLOBAL_ADMIN);

        assertDoesNotThrow(() -> tenantService.getTenantById(tenantA.id()));
        assertDoesNotThrow(() -> tenantService.getTenantById(tenantB.id()));
    }

    private void setSecurityContext(UUID userId, UUID tenantId, String role) {
        UserPrincipal principal = new UserPrincipal(
                userId, tenantId, "test@" + role.toLowerCase() + ".com", "pass", role, true);
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
        } else {
            TenantContext.clear();
        }
    }
}
