package com.umis.dashboard.dto;

import java.time.LocalDate;

/**
 * Daily trend entry for dashboard charts.
 */
public record DailyTrendDto(
        LocalDate date,
        long discovered,
        long ingested,
        long published,
        long parsedSuccess,
        long failed
) {}
