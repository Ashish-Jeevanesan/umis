package com.umis.tenant.service;

import com.umis.common.exception.ResourceNotFoundException;
import com.umis.common.exception.UnauthorizedException;
import com.umis.security.JwtTokenProvider;
import com.umis.security.RoleConstants;
import com.umis.security.UserPrincipal;
import com.umis.tenant.dto.AuthRequest;
import com.umis.tenant.dto.AuthResponse;
import com.umis.tenant.model.Tenant;
import com.umis.tenant.model.TenantStatus;
import com.umis.tenant.model.TenantUser;
import com.umis.tenant.model.User;
import com.umis.tenant.repository.TenantRepository;
import com.umis.tenant.repository.TenantUserRepository;
import com.umis.tenant.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Service managing user authentication, token generation, and initial seed accounts.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TenantUserRepository tenantUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider
    ) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @PostConstruct
    @Transactional
    public void initDefaultAccounts() {
        if (userRepository.count() == 0) {
            // Seed Global Admin
            User globalAdmin = new User("admin@umis.local", "Global Administrator",
                    passwordEncoder.encode("Admin@123"), RoleConstants.GLOBAL_ADMIN);
            userRepository.save(globalAdmin);

            // Seed Tenant A
            Tenant tenantA = tenantRepository.save(new Tenant("tenant-a", "Acme Corporation"));
            User userA = userRepository.save(new User("admin@acme.com", "Acme Admin",
                    passwordEncoder.encode("Admin@123"), "TENANT_USER"));
            tenantUserRepository.save(new TenantUser(tenantA, userA, RoleConstants.TENANT_ADMIN));

            // Seed Tenant B
            Tenant tenantB = tenantRepository.save(new Tenant("tenant-b", "Globex Industries"));
            User userB = userRepository.save(new User("admin@globex.com", "Globex Admin",
                    passwordEncoder.encode("Admin@123"), "TENANT_USER"));
            tenantUserRepository.save(new TenantUser(tenantB, userB, RoleConstants.TENANT_ADMIN));
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse authenticate(AuthRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isActive()) {
            throw new UnauthorizedException("User account is inactive");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (RoleConstants.GLOBAL_ADMIN.equals(user.getSystemRole())) {
            return buildGlobalAdminResponse(user);
        }

        return buildTenantUserResponse(user, request.tenantCode());
    }

    private AuthResponse buildGlobalAdminResponse(User user) {
        UserPrincipal principal = new UserPrincipal(
                user.getId(), null, user.getEmail(), user.getPasswordHash(), RoleConstants.GLOBAL_ADMIN, true);
        String token = tokenProvider.generateToken(principal);
        return AuthResponse.of(token, user.getId(), user.getEmail(), user.getFullName(),
                RoleConstants.GLOBAL_ADMIN, null, null, "Global Administrator");
    }

    private AuthResponse buildTenantUserResponse(User user, String preferredTenantCode) {
        List<TenantUser> memberships = tenantUserRepository.findByUserIdWithTenant(user.getId());
        if (memberships.isEmpty()) {
            throw new UnauthorizedException("User is not associated with any organization");
        }

        TenantUser selected = memberships.stream()
                .filter(m -> !StringUtils.hasText(preferredTenantCode)
                        || m.getTenant().getCode().equalsIgnoreCase(preferredTenantCode))
                .findFirst()
                .orElse(memberships.get(0));

        Tenant tenant = selected.getTenant();
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new UnauthorizedException("Organization account is suspended");
        }

        UserPrincipal principal = new UserPrincipal(
                user.getId(), tenant.getId(), user.getEmail(), user.getPasswordHash(), selected.getRole(), true);
        String token = tokenProvider.generateToken(principal);

        return AuthResponse.of(token, user.getId(), user.getEmail(), user.getFullName(),
                selected.getRole(), tenant.getId(), tenant.getCode(), tenant.getName());
    }
}
