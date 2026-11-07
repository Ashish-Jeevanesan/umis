package com.umis.dashboard.controller;

import com.umis.common.api.ApiResponse;
import com.umis.dashboard.dto.DailyTrendDto;
import com.umis.dashboard.dto.GlobalDashboardDto;
import com.umis.dashboard.dto.SystemHealthDto;
import com.umis.dashboard.dto.TenantDashboardDto;
import com.umis.dashboard.service.DashboardService;
import com.umis.mailbox.dto.MailboxDto;
import com.umis.mailbox.service.MailboxService;
import com.umis.security.RoleConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller serving operational dashboard metrics, trends, and system health status.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Operational dashboards and metrics reporting APIs")
public class DashboardController {

    private final DashboardService dashboardService;
    private final MailboxService mailboxService;

    public DashboardController(DashboardService dashboardService, MailboxService mailboxService) {
        this.dashboardService = dashboardService;
        this.mailboxService = mailboxService;
    }

    @GetMapping("/global")
    @PreAuthorize(RoleConstants.HAS_GLOBAL_ADMIN)
    @Operation(summary = "Get aggregated metrics and status for the Global Admin dashboard")
    public ResponseEntity<ApiResponse<GlobalDashboardDto>> getGlobalDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getGlobalDashboard()));
    }

    @GetMapping("/global/trends")
    @PreAuthorize(RoleConstants.HAS_GLOBAL_ADMIN)
    @Operation(summary = "Get global daily ingestion and processing trends")
    public ResponseEntity<ApiResponse<List<DailyTrendDto>>> getGlobalTrends(
            @RequestParam(defaultValue = "14") int days
    ) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDailyTrends(null, days)));
    }

    @GetMapping("/tenant")
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "Get organization metrics and summary for Tenant Admin dashboard")
    public ResponseEntity<ApiResponse<TenantDashboardDto>> getTenantDashboard(
            @RequestParam(required = false) UUID tenantId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getTenantDashboard(tenantId)));
    }

    @GetMapping("/tenant/trends")
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "Get tenant-scoped daily ingestion and downstream trends")
    public ResponseEntity<ApiResponse<List<DailyTrendDto>>> getTenantTrends(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "7") int days
    ) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDailyTrends(tenantId, days)));
    }

    @GetMapping("/tenant/mailboxes")
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "Get connected mailboxes with health indicators for tenant dashboard")
    public ResponseEntity<ApiResponse<List<MailboxDto>>> getTenantMailboxes(
            @RequestParam(required = false) UUID tenantId
    ) {
        List<MailboxDto> mailboxes = mailboxService.listMailboxes(
                tenantId, PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
        return ResponseEntity.ok(ApiResponse.ok(mailboxes));
    }

    @GetMapping("/health")
    @Operation(summary = "Get system operational health and subsystem connectivity status")
    public ResponseEntity<ApiResponse<SystemHealthDto>> getHealth() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getSystemHealth()));
    }
}
