package com.po.processor.hospital.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data transfer object encapsulating lifetime purchasing history for a canonical hospital.
 */
public record HospitalHistoryDto(
        UUID hospitalId,
        String hospitalName,
        String legalName,
        String hospitalCode,
        long totalApprovedPoCount,
        Map<String, BigDecimal> approvedTotalsByCurrency,
        LocalDate firstPoReceivedDate,
        LocalDate latestPoReceivedDate,
        long duplicateSubmissionsCount,
        List<HistoricalPoSummaryDto> historicalPurchaseOrders
) {
    /**
     * Compact summary for an individual historical purchase order.
     */
    public record HistoricalPoSummaryDto(
            UUID poId,
            String poNumber,
            LocalDate poDate,
            BigDecimal grandTotal,
            String currency,
            String vendorName,
            boolean isApproved
    ) {}
}
