package com.umis.tenant.service;

import com.umis.common.exception.ConflictException;
import com.umis.common.exception.ResourceNotFoundException;
import com.umis.common.exception.UnauthorizedException;
import com.umis.security.RoleConstants;
import com.umis.security.TenantContext;
import com.umis.security.UserPrincipal;
import com.umis.tenant.dto.CreateTenantRequest;
import com.umis.tenant.dto.TenantDto;
import com.umis.tenant.dto.UpdateTenantRequest;
import com.umis.tenant.model.Tenant;
import com.umis.tenant.model.TenantStatus;
import com.umis.tenant.model.TenantUser;
import com.umis.tenant.model.User;
import com.umis.tenant.repository.TenantRepository;
import com.umis.tenant.repository.TenantUserRepository;
import com.umis.tenant.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Service managing tenant lifecycle and tenant authorization validation.
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TenantDto createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsByCode(request.code())) {
            throw new ConflictException("Tenant with code '" + request.code() + "' already exists");
        }

        Tenant tenant = new Tenant(request.code(), request.name());
        Tenant savedTenant = tenantRepository.save(tenant);

        if (StringUtils.hasText(request.adminEmail())) {
            createTenantAdminUser(savedTenant, request);
        }

        return TenantDto.from(savedTenant);
    }

    private void createTenantAdminUser(Tenant tenant, CreateTenantRequest request) {
        String rawPassword = StringUtils.hasText(request.adminPassword()) ? request.adminPassword() : "Admin@123";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        String fullName = StringUtils.hasText(request.adminFullName()) ? request.adminFullName() : "Tenant Admin";

        User user = userRepository.findByEmail(request.adminEmail())
                .orElseGet(() -> userRepository.save(
                        new User(request.adminEmail(), fullName, encodedPassword, "TENANT_USER")
                ));

        TenantUser tenantUser = new TenantUser(tenant, user, RoleConstants.TENANT_ADMIN);
        tenantUserRepository.save(tenantUser);
    }

    @Transactional(readOnly = true)
    public TenantDto getTenantById(UUID tenantId) {
        validateTenantAccess(tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with ID: " + tenantId));
        return TenantDto.from(tenant);
    }

    @Transactional(readOnly = true)
    public Page<TenantDto> listTenants(TenantStatus status, Pageable pageable) {
        Page<Tenant> page = (status != null)
                ? tenantRepository.findAllByStatus(status, pageable)
                : tenantRepository.findAll(pageable);
        return page.map(TenantDto::from);
    }

    @Transactional
    public TenantDto updateTenant(UUID tenantId, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with ID: " + tenantId));

        if (StringUtils.hasText(request.name())) {
            tenant.setName(request.name());
        }

        return TenantDto.from(tenantRepository.save(tenant));
    }

    @Transactional
    public TenantDto suspendTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with ID: " + tenantId));
        tenant.setStatus(TenantStatus.SUSPENDED);
        return TenantDto.from(tenantRepository.save(tenant));
    }

    @Transactional
    public TenantDto activateTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with ID: " + tenantId));
        tenant.setStatus(TenantStatus.ACTIVE);
        return TenantDto.from(tenantRepository.save(tenant));
    }

    public void validateTenantAccess(UUID targetTenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("User is not authenticated");
        }

        boolean isGlobalAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(RoleConstants.ROLE_GLOBAL_ADMIN));
        if (isGlobalAdmin) {
            return;
        }

        UUID contextTenantId = TenantContext.getTenantId();
        if (contextTenantId == null || !contextTenantId.equals(targetTenantId)) {
            throw new UnauthorizedException("Access denied to tenant data: " + targetTenantId);
        }
    }
}
