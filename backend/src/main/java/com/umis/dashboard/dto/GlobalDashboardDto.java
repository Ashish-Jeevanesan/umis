package com.umis.dashboard.dto;

import com.umis.tenant.dto.TenantDto;

import java.util.List;

/**
 * Summary metrics and operational status for the Global Administrator dashboard.
 */
public record GlobalDashboardDto(
        long totalTenants,
        long activeTenants,
        long connectedMailboxes,
        long totalEmailsDiscovered,
        long totalEmailsIngested,
        long totalEmailsPublished,
        long emailsParsedSuccess,
        long emailsFailed,
        List<ProviderDistributionDto> providerDistribution,
        List<TenantDto> recentTenants,
        List<DailyTrendDto> recentTrends
) {}
