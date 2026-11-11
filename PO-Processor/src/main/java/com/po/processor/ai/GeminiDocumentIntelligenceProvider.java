package com.po.processor.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.po.processor.ai.dto.DocumentExtractionRequest;
import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini implementation of DocumentIntelligenceProvider using official Generative Language REST API.
 * Hardened with bounded exponential backoff, jitter, and error-status filtering.
 */
@Service
public class GeminiDocumentIntelligenceProvider implements DocumentIntelligenceProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiDocumentIntelligenceProvider.class);

    private final GeminiPromptBuilder promptBuilder;
    private final GeminiResponseParser responseParser;
    private final RestClient restClient;
    private final String strApiKey;
    private final String strModelName;
    private final boolean isEnabled;
    private final int maxOutputTokens;
    private final int maxRetries;
    private final ObjectMapper objectMapper;

    public GeminiDocumentIntelligenceProvider(
            GeminiPromptBuilder promptBuilder,
            GeminiResponseParser responseParser,
            @Value("${po.gemini.api-key:}") String strApiKey,
            @Value("${po.gemini.model:gemini-3.8-flash}") String strModelName,
            @Value("${po.gemini.enabled:true}") boolean isEnabled,
            @Value("${po.gemini.base-url:https://generativelanguage.googleapis.com}") String strBaseUrl,
            @Value("${po.gemini.timeout-seconds:60}") int timeoutSeconds,
            @Value("${po.gemini.max-output-tokens:8192}") int maxOutputTokens,
            @Value("${po.gemini.max-retries:3}") int maxRetries
    ) {
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.strApiKey = strApiKey;
        if (strModelName == null || strModelName.isBlank() || "gemini-1.5-flash".equalsIgnoreCase(strModelName)) {
            this.strModelName = "gemini-3.8-flash";
        } else {
            this.strModelName = strModelName;
        }
        this.isEnabled = isEnabled;
        this.maxOutputTokens = maxOutputTokens;
        this.maxRetries = maxRetries;
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(strBaseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public ExtractedPurchaseOrderDto extractPurchaseOrder(DocumentExtractionRequest request) {
        if (!isEnabled || strApiKey == null || strApiKey.isBlank()) {
            log.info("Gemini extraction disabled or API key absent. Returning mock fallback extraction.");
            return createFallbackExtraction(request);
        }

        String strPrompt = promptBuilder.buildExtractionPrompt(request);
        Map<String, Object> hmRequestBody = buildGeminiPayload(strPrompt, request);

        int currentAttempt = 1;
        long initialBackoffMs = 1000;

        while (currentAttempt <= maxRetries) {
            try {
                String strUrl = "/v1beta/models/" + strModelName + ":generateContent";

                String strResponse = restClient.post()
                        .uri(strUrl)
                        .header("x-goog-api-key", strApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(hmRequestBody)
                        .retrieve()
                        .body(String.class);

                return responseParser.parseResponse(strResponse);
            } catch (RestClientResponseException ex) {
                int statusCode = ex.getStatusCode().value();
                if (isRetryableStatus(statusCode) && currentAttempt < maxRetries) {
                    long delayMs = calculateDelay(ex, initialBackoffMs, currentAttempt);
                    log.warn("Gemini API HTTP {} on attempt {}. Retrying in {} ms", statusCode, currentAttempt, delayMs);
                    sleep(delayMs);
                    currentAttempt++;
                } else {
                    log.error("Gemini API non-retryable failure status: {}", statusCode);
                    throw new IllegalStateException("Gemini extraction failed with HTTP " + statusCode + ": " + ex.getMessage(), ex);
                }
            } catch (ResourceAccessException ex) {
                if (currentAttempt < maxRetries) {
                    long delayMs = calculateJitterBackoff(initialBackoffMs, currentAttempt);
                    log.warn("Gemini network/timeout on attempt {}. Retrying in {} ms", currentAttempt, delayMs);
                    sleep(delayMs);
                    currentAttempt++;
                } else {
                    log.error("Gemini network failure after max attempts", ex);
                    throw new IllegalStateException("Gemini network timeout/failure: " + ex.getMessage(), ex);
                }
            } catch (Exception ex) {
                log.error("Gemini extraction invocation failed", ex);
                throw new IllegalStateException("Gemini extraction failed: " + ex.getMessage(), ex);
            }
        }

        throw new IllegalStateException("Gemini extraction failed after max retry attempts");
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || (statusCode >= 500 && statusCode <= 504);
    }

    private long calculateDelay(RestClientResponseException ex, long initialBackoffMs, int attempt) {
        String strRetryAfter = ex.getResponseHeaders() != null ? ex.getResponseHeaders().getFirst("Retry-After") : null;
        if (strRetryAfter != null) {
            try {
                return Long.parseLong(strRetryAfter.trim()) * 1000L;
            } catch (NumberFormatException ignored) {}
        }
        return calculateJitterBackoff(initialBackoffMs, attempt);
    }

    private long calculateJitterBackoff(long initialBackoffMs, int attempt) {
        long exponential = initialBackoffMs * (1L << (attempt - 1));
        long jitter = (long) (Math.random() * 400);
        return exponential + jitter;
    }

    private Map<String, Object> buildGeminiPayload(String strPrompt, DocumentExtractionRequest request) {
        Map<String, Object> hmPayload = new HashMap<>();
        List<Map<String, Object>> alParts = new ArrayList<>();

        alParts.add(Map.of("text", strPrompt));

        if (request.attachments() != null) {
            for (DocumentExtractionRequest.DocumentAttachmentInput att : request.attachments()) {
                if (att.base64Content() != null && !att.base64Content().isBlank()) {
                    Map<String, Object> hmInlineData = Map.of(
                            "mime_type", "image/jpeg",
                            "data", att.base64Content()
                    );
                    alParts.add(Map.of("inline_data", hmInlineData));
                }
            }
        }

        List<Map<String, Object>> alContents = List.of(Map.of("parts", alParts));
        hmPayload.put("contents", alContents);

        Map<String, Object> hmGenConfig = Map.of(
                "response_mime_type", "application/json",
                "temperature", 0.1,
                "maxOutputTokens", maxOutputTokens
        );
        hmPayload.put("generationConfig", hmGenConfig);

        return hmPayload;
    }

    private ExtractedPurchaseOrderDto createFallbackExtraction(DocumentExtractionRequest request) {
        String strPoNum = "PO-SAMPLE-001";
        String strHospital = "Sample General Hospital";
        String strVendor = "Sample Vendor LLC";
        java.math.BigDecimal bdGrandTotal = new java.math.BigDecimal("1100.00");
        java.math.BigDecimal bdSubtotal = new java.math.BigDecimal("1000.00");
        java.math.BigDecimal bdTax = new java.math.BigDecimal("100.00");
        String strCurrency = "USD";
        List<com.po.processor.ai.dto.ExtractedLineItemDto> alItems = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        if (request.emailSubject() != null) sb.append(request.emailSubject()).append("\n");
        if (request.emailBodyText() != null) sb.append(request.emailBodyText()).append("\n");
        if (request.attachments() != null) {
            for (var att : request.attachments()) {
                if (att.extractedText() != null) sb.append(att.extractedText()).append("\n");
            }
        }
        String docText = sb.toString();

        for (String line : docText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("PURCHASE ORDER:") || trimmed.startsWith("PO Number:")) {
                String val = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (!val.isBlank()) strPoNum = val;
            } else if (trimmed.startsWith("Hospital:") || trimmed.startsWith("Customer:")) {
                String val = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (!val.isBlank()) strHospital = val;
            } else if (trimmed.startsWith("Vendor:") || trimmed.startsWith("Supplier:")) {
                String val = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (!val.isBlank()) strVendor = val;
            } else if (trimmed.startsWith("Grand Total:") || trimmed.startsWith("Total Amount:")) {
                String val = trimmed.substring(trimmed.indexOf(':') + 1).trim().replace("USD", "").replace("$", "").trim();
                try { bdGrandTotal = new java.math.BigDecimal(val); } catch (Exception ignored) {}
            } else if (trimmed.startsWith("Subtotal:")) {
                String val = trimmed.substring(trimmed.indexOf(':') + 1).trim().replace("USD", "").replace("$", "").trim();
                try { bdSubtotal = new java.math.BigDecimal(val); } catch (Exception ignored) {}
            } else if (trimmed.startsWith("Tax:")) {
                String val = trimmed.substring(trimmed.indexOf(':') + 1).trim().replace("USD", "").replace("$", "").trim();
                try { bdTax = new java.math.BigDecimal(val); } catch (Exception ignored) {}
            } else if (trimmed.startsWith("Line ") && trimmed.contains("|")) {
                String[] parts = trimmed.split("\\|");
                if (parts.length >= 7) {
                    int lineNo = alItems.size() + 1;
                    String code = parts[1].trim();
                    String desc = parts[2].trim();
                    String qtyStr = parts[3].replace("Qty:", "").trim();
                    String unit = parts[4].trim();
                    String priceStr = parts[5].replace("Price:", "").trim();
                    String totalStr = parts[6].replace("Total:", "").trim();
                    try {
                        alItems.add(new com.po.processor.ai.dto.ExtractedLineItemDto(
                                lineNo, code, code, desc, desc, "Medical",
                                new java.math.BigDecimal(qtyStr), unit, new java.math.BigDecimal(priceStr),
                                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                                new java.math.BigDecimal(totalStr), new java.math.BigDecimal(totalStr), strCurrency, null, null
                        ));
                    } catch (Exception ignored) {}
                }
            }
        }

        if (alItems.isEmpty()) {
            alItems.add(new com.po.processor.ai.dto.ExtractedLineItemDto(
                    1, "ITEM-01", "MFG-01", "Medical Supply Box", "Standard Surgical Pack",
                    "Surgical", new java.math.BigDecimal("2.000"), "BOX", new java.math.BigDecimal("500.00"),
                    java.math.BigDecimal.ZERO, new java.math.BigDecimal("10.00"), new java.math.BigDecimal("100.00"),
                    new java.math.BigDecimal("1000.00"), new java.math.BigDecimal("1100.00"), "USD", null, null
            ));
        }

        return new ExtractedPurchaseOrderDto(
                strPoNum,
                request.emailSubject(),
                java.time.LocalDate.now(),
                null,
                null,
                strCurrency,
                bdSubtotal,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                bdTax,
                new java.math.BigDecimal("10.00"),
                bdGrandTotal,
                "Net 30",
                "Wire Transfer",
                null,
                null,
                null,
                null,
                strHospital,
                "HOSP-01",
                new com.po.processor.ai.dto.ExtractedVendorDto(
                        strVendor, "123 Supply Way", "Sales Rep",
                        "vendor@supply.com", "555-0100", "TAX-9988", "ACC-1234"
                ),
                null,
                null,
                null,
                alItems,
                List.of()
        );
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String getProviderName() {
        return "GEMINI";
    }

    @Override
    public String getModelName() {
        return strModelName;
    }
}
