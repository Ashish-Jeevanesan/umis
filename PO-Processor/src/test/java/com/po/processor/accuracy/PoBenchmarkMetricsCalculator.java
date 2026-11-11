package com.po.processor.accuracy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

/**
 * Calculates statistical metrics, latency percentiles, token usage, and cost estimates for AI benchmarks.
 */
public class PoBenchmarkMetricsCalculator {

    // Official Google Gemini 1.5 Flash API pricing per 1M tokens (as of 2024/2025/2026)
    public static final BigDecimal COST_PER_MILLION_INPUT_TOKENS = new BigDecimal("0.075");
    public static final BigDecimal COST_PER_MILLION_OUTPUT_TOKENS = new BigDecimal("0.30");

    /**
     * Computes the percentile from a list of sorted durations.
     *
     * @param alSortedDurations Sorted list of latencies in milliseconds
     * @param percentile Percentile to calculate (e.g., 50.0 for median, 95.0 for P95)
     * @return Latency at given percentile
     */
    public static long calculatePercentile(List<Long> alSortedDurations, double percentile) {
        if (alSortedDurations == null || alSortedDurations.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil((percentile / 100.0) * alSortedDurations.size()) - 1;
        int safeIndex = Math.max(0, Math.min(index, alSortedDurations.size() - 1));
        return alSortedDurations.get(safeIndex);
    }

    /**
     * Estimates monetary cost in USD based on Gemini Flash token consumption.
     *
     * @param totalInputTokens Cumulative input prompt tokens
     * @param totalOutputTokens Cumulative output completion tokens
     * @return Estimated cost in USD
     */
    public static BigDecimal estimateTotalCostUsd(long totalInputTokens, long totalOutputTokens) {
        BigDecimal inputCost = new BigDecimal(totalInputTokens)
                .divide(new BigDecimal("1000000"), 8, RoundingMode.HALF_UP)
                .multiply(COST_PER_MILLION_INPUT_TOKENS);

        BigDecimal outputCost = new BigDecimal(totalOutputTokens)
                .divide(new BigDecimal("1000000"), 8, RoundingMode.HALF_UP)
                .multiply(COST_PER_MILLION_OUTPUT_TOKENS);

        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Estimates cost to process 1,000 purchase orders based on sample average cost.
     *
     * @param avgCostPerDocument Average cost for a single document
     * @return Projected cost for 1,000 purchase orders
     */
    public static BigDecimal projectCostPerThousand(BigDecimal avgCostPerDocument) {
        if (avgCostPerDocument == null) {
            return BigDecimal.ZERO;
        }
        return avgCostPerDocument.multiply(new BigDecimal("1000")).setScale(2, RoundingMode.HALF_UP);
    }
}
