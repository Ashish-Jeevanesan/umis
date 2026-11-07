package com.umis.tenant.controller;

import com.umis.common.api.ApiResponse;
import com.umis.common.api.PageResponse;
import com.umis.security.RoleConstants;
import com.umis.tenant.dto.CreateTenantRequest;
import com.umis.tenant.dto.TenantDto;
import com.umis.tenant.dto.UpdateTenantRequest;
import com.umis.tenant.model.TenantStatus;
import com.umis.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for tenant management operations.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenants", description = "Tenant organization management APIs")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @PreAuthorize(RoleConstants.HAS_GLOBAL_ADMIN)
    @Operation(summary = "Create a new organization tenant")
    public ResponseEntity<ApiResponse<TenantDto>> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        TenantDto created = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Tenant created successfully", created));
    }

    @GetMapping
    @PreAuthorize(RoleConstants.HAS_GLOBAL_ADMIN)
    @Operation(summary = "List all tenants with optional status filter")
    public ResponseEntity<ApiResponse<PageResponse<TenantDto>>> listTenants(
            @RequestParam(required = false) TenantStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<TenantDto> page = PageResponse.from(tenantService.listTenants(status, pageable));
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "Get tenant details by ID")
    public ResponseEntity<ApiResponse<TenantDto>> getTenant(@PathVariable UUID id) {
        TenantDto tenant = tenantService.getTenantById(id);
        return ResponseEntity.ok(ApiResponse.ok(tenant));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(RoleConstants.HAS_GLOBAL_ADMIN)
    @Operation(summary = "Update tenant details")
    public ResponseEntity<ApiResponse<TenantDto>> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        TenantDto updated = tenantService.updateTenant(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Tenant updated successfully", updated));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize(RoleConstants.HAS_GLOBAL_ADMIN)
    @Operation(summary = "Suspend a tenant organization")
    public ResponseEntity<ApiResponse<TenantDto>> suspendTenant(@PathVariable UUID id) {
        TenantDto suspended = tenantService.suspendTenant(id);
        return ResponseEntity.ok(ApiResponse.ok("Tenant suspended successfully", suspended));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(RoleConstants.HAS_GLOBAL_ADMIN)
    @Operation(summary = "Activate a suspended tenant organization")
    public ResponseEntity<ApiResponse<TenantDto>> activateTenant(@PathVariable UUID id) {
        TenantDto activated = tenantService.activateTenant(id);
        return ResponseEntity.ok(ApiResponse.ok("Tenant activated successfully", activated));
    }
}
