package com.po.processor.accuracy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.po.processor.ai.GeminiDocumentIntelligenceProvider;
import com.po.processor.ai.GeminiModelPreflightService;
import com.po.processor.ai.GeminiPromptBuilder;
import com.po.processor.ai.GeminiResponseParser;
import com.po.processor.ai.dto.DocumentExtractionRequest;
import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import com.po.processor.comparison.BodyAttachmentComparator;
import com.po.processor.hospital.HospitalMasterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controlled live Gemini AI benchmark runner evaluating inference, accuracy, latency, and costs across 30 layouts.
 */
public class LivePoGeminiExtractionBenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(LivePoGeminiExtractionBenchmarkRunner.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BodyAttachmentComparator comparator = new BodyAttachmentComparator();
    private final HospitalMasterService hospitalService = new HospitalMasterService(null, null, null, null);

    @Test
    @DisplayName("Execute Controlled Live Gemini AI Extraction Benchmark across 30 Document Categories")
    void executeLiveExtractionBenchmark() throws Exception {
        String strApiKey = System.getenv("GEMINI_API_KEY");
        if (strApiKey == null || strApiKey.isBlank()) {
            strApiKey = System.getProperty("GEMINI_API_KEY");
        }
        String strModelName = System.getenv("GEMINI_MODEL") != null ? System.getenv("GEMINI_MODEL") : "gemini-3.8-flash";

        // Preflight model validation
        GeminiModelPreflightService preflightService = new GeminiModelPreflightService(
                strApiKey, strModelName, "https://generativelanguage.googleapis.com"
        );
        GeminiModelPreflightService.PreflightResult preflight = preflightService.executePreflightCheck();

        log.info("Gemini Preflight Result: Model [{}] -> Available: {}, JSON: {}, Vision: {}",
                preflight.configuredModel(), preflight.isAvailable(),
                preflight.structuredJsonSupported(), preflight.multimodalSupported());

        // Load 30 layout catalog
        InputStream isCatalog = getClass().getResourceAsStream("/test-data/purchase-orders/test_cases_catalog.json");
        if (isCatalog == null) {
            isCatalog = new java.io.FileInputStream("test-data/purchase-orders/test_cases_catalog.json");
        }
        assertNotNull(isCatalog, "test_cases_catalog.json must be readable");

        List<PoExtractionAccuracyTestRunner.TestCase> alCases =
                objectMapper.readValue(isCatalog, new TypeReference<List<PoExtractionAccuracyTestRunner.TestCase>>() {});
        assertFalse(alCases.isEmpty(), "Catalog must contain test cases");

        GeminiDocumentIntelligenceProvider provider = new GeminiDocumentIntelligenceProvider(
                new GeminiPromptBuilder(), new GeminiResponseParser(),
                strApiKey, strModelName, preflight.isAvailable(),
                "https://generativelanguage.googleapis.com", 60, 8192, 3
        );

        List<Long> alLatencies = new ArrayList<>();
        int digitalEvaluations = 0, digitalPassed = 0;
        int scannedEvaluations = 0, scannedPassed = 0;
        int totalFieldEvaluations = 0, totalPassed = 0;
        long totalInputTokens = 0, totalOutputTokens = 0;

        StringBuilder sbReport = new StringBuilder();
        sbReport.append("\n========================================================================================\n");
        sbReport.append(String.format("   PO PROCESSOR — PHASE D: LIVE GEMINI INFERENCE BENCHMARK REPORT [%s]\n", strModelName));
        sbReport.append("========================================================================================\n\n");

        for (PoExtractionAccuracyTestRunner.TestCase tc : alCases) {
            long startTime = System.currentTimeMillis();

            List<DocumentExtractionRequest.DocumentAttachmentInput> alAtts = new ArrayList<>();
            if (tc.attachmentName() != null) {
                String strMethod = tc.category().contains("SCANNED") || tc.category().contains("IMAGE")
                        ? "IMAGE_VISION_EXTRACTION" : "DIGITAL_TEXT_EXTRACTION";
                alAtts.add(new DocumentExtractionRequest.DocumentAttachmentInput(
                        UUID.randomUUID(), tc.attachmentName(), "application/pdf",
                        tc.attachmentText(), null, 1, strMethod
                ));
            }

            DocumentExtractionRequest req = new DocumentExtractionRequest(
                    UUID.randomUUID(), UUID.randomUUID(), tc.emailSubject(),
                    "sender@hospital.org", tc.emailBody(), alAtts
            );

            ExtractedPurchaseOrderDto extracted = provider.extractPurchaseOrder(req);
            long latencyMs = System.currentTimeMillis() - startTime;
            alLatencies.add(latencyMs);

            // Estimate token consumption based on prompt text size
            int estimatedInTokens = Math.max(100, (tc.emailBody() + (tc.attachmentText() != null ? tc.attachmentText() : "")).length() / 3);
            int estimatedOutTokens = 350;
            totalInputTokens += estimatedInTokens;
            totalOutputTokens += estimatedOutTokens;

            int passedInCase = evaluateCase(tc, extracted);
            int fieldsInCase = 5;

            totalPassed += passedInCase;
            totalFieldEvaluations += fieldsInCase;

            boolean isScanned = tc.category().contains("SCANNED") || tc.category().contains("IMAGE");
            if (isScanned) {
                scannedPassed += passedInCase;
                scannedEvaluations += fieldsInCase;
            } else {
                digitalPassed += passedInCase;
                digitalEvaluations += fieldsInCase;
            }

            sbReport.append(String.format("Case %02d [%-18s]: %-40s | %4d ms | %d/%d fields (%.1f%%)\n",
                    tc.caseId(), tc.category(), tc.name(), latencyMs, passedInCase, fieldsInCase,
                    ((double) passedInCase / fieldsInCase) * 100.0));
        }

        Collections.sort(alLatencies);
        long avgLatency = (long) alLatencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long medianLatency = PoBenchmarkMetricsCalculator.calculatePercentile(alLatencies, 50.0);
        long p95Latency = PoBenchmarkMetricsCalculator.calculatePercentile(alLatencies, 95.0);

        BigDecimal totalCost = PoBenchmarkMetricsCalculator.estimateTotalCostUsd(totalInputTokens, totalOutputTokens);
        BigDecimal avgCostPerDoc = totalCost.divide(new BigDecimal(alCases.size()), 6, RoundingMode.HALF_UP);
        BigDecimal costPerThousand = PoBenchmarkMetricsCalculator.projectCostPerThousand(avgCostPerDoc);

        double overallAccuracy = ((double) totalPassed / totalFieldEvaluations) * 100.0;
        double digitalAccuracy = digitalEvaluations > 0 ? ((double) digitalPassed / digitalEvaluations) * 100.0 : 0.0;
        double scannedAccuracy = scannedEvaluations > 0 ? ((double) scannedPassed / scannedEvaluations) * 100.0 : 0.0;

        sbReport.append("\n----------------------------------------------------------------------------------------\n");
        sbReport.append(String.format("OVERALL ACCURACY:            %.2f%% (%d / %d fields evaluated)\n", overallAccuracy, totalPassed, totalFieldEvaluations));
        sbReport.append(String.format("DIGITAL PDF ACCURACY:        %.2f%% (%d / %d fields)\n", digitalAccuracy, digitalPassed, digitalEvaluations));
        sbReport.append(String.format("SCANNED/VISION ACCURACY:     %.2f%% (%d / %d fields)\n", scannedAccuracy, scannedPassed, scannedEvaluations));
        sbReport.append(String.format("LATENCY (Avg / Med / P95):   %d ms / %d ms / %d ms\n", avgLatency, medianLatency, p95Latency));
        sbReport.append(String.format("TOKEN USAGE (In / Out / Tot): %d / %d / %d tokens\n", totalInputTokens, totalOutputTokens, totalInputTokens + totalOutputTokens));
        sbReport.append(String.format("ESTIMATED COST (Total / Avg): $%s USD / $%s USD per PO\n", totalCost, avgCostPerDoc));
        sbReport.append(String.format("PROJECTED COST (1,000 POs):  $%s USD\n", costPerThousand));
        sbReport.append("========================================================================================\n");

        log.info(sbReport.toString());
        if (preflight.isAvailable()) {
            assertTrue(overallAccuracy >= 80.0,
                    String.format("Benchmark extraction accuracy must meet or exceed 80.00%% (Achieved: %.2f%%)", overallAccuracy));
        } else {
            log.warn("Live Gemini inference skipped: GEMINI_API_KEY is not configured in process environment. "
                    + "Provide GEMINI_API_KEY to execute live inference against Google Generative Language API.");
        }
    }

    private int evaluateCase(PoExtractionAccuracyTestRunner.TestCase tc, ExtractedPurchaseOrderDto extracted) {
        int passed = 0;
        if (matchesString(tc.expected().poNumber(), extracted.poNumber())) passed++;
        String strNormHosp = hospitalService.normalizeHospitalName(extracted.hospitalName());
        if (matchesString(tc.expected().hospitalName(), strNormHosp)) passed++;
        String strVendor = extracted.vendor() != null ? extracted.vendor().vendorName() : null;
        if (matchesString(tc.expected().vendorName(), strVendor)) passed++;
        if (matchesString(tc.expected().currency(), extracted.currency())) passed++;
        if (matchesAmount(tc.expected().grandTotal(), extracted.grandTotal())) passed++;
        return passed;
    }

    private boolean matchesString(String expected, String actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;
        return expected.equalsIgnoreCase(actual);
    }

    private boolean matchesAmount(BigDecimal expected, BigDecimal actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;
        return expected.compareTo(actual) == 0;
    }
}
