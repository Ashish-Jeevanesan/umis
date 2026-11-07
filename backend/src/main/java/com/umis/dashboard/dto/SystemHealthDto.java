package com.umis.dashboard.dto;

import java.time.Instant;
import java.util.Map;

/**
 * System operational health status report.
 */
public record SystemHealthDto(
        String status, // UP, DEGRADED, DOWN
        Instant timestamp,
        boolean databaseConnected,
        boolean rabbitMqConnected,
        boolean storageConnected,
        long pendingOutboxCount,
        long failedMailboxesCount,
        Map<String, Object> componentDetails
) {}
