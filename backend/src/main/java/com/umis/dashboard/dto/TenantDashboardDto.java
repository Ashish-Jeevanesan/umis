package com.umis.dashboard.dto;

import com.umis.mailbox.dto.MailboxDto;

import java.util.List;
import java.util.UUID;

/**
 * Summary metrics and operational status for the individual Tenant Administrator dashboard.
 */
public record TenantDashboardDto(
        UUID tenantId,
        String tenantName,
        long connectedMailboxes,
        long emailsDiscovered,
        long emailsPicked,
        long emailsIngested,
        long emailsPublished,
        long emailsParsedSuccess,
        long emailsPending,
        long emailsFailed,
        List<MailboxDto> mailboxes,
        List<DailyTrendDto> trends
) {}
