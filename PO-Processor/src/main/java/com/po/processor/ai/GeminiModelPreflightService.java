package com.po.processor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service performing live preflight probes against the Google Gemini API to verify model availability and capability.
 */
@Service
public class GeminiModelPreflightService {

    private static final Logger log = LoggerFactory.getLogger(GeminiModelPreflightService.class);
    private static final String TINY_PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    private final RestClient restClient;
    private final String strApiKey;
    private final String strModelName;
    private final ObjectMapper objectMapper;

    public GeminiModelPreflightService(
            @Value("${po.gemini.api-key:}") String strApiKey,
            @Value("${po.gemini.model:gemini-3.8-flash}") String strModelName,
            @Value("${po.gemini.base-url:https://generativelanguage.googleapis.com}") String strBaseUrl
    ) {
        this.strApiKey = strApiKey;
        if (strModelName == null || strModelName.isBlank() || "gemini-1.5-flash".equalsIgnoreCase(strModelName)) {
            this.strModelName = "gemini-3.8-flash";
        } else {
            this.strModelName = strModelName;
        }
        this.objectMapper = new ObjectMapper();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));

        this.restClient = RestClient.builder()
                .baseUrl(strBaseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * Preflight evaluation outcome.
     */
    public record PreflightResult(
            boolean isAvailable,
            String configuredModel,
            String apiVersion,
            boolean structuredJsonSupported,
            boolean multimodalSupported,
            String diagnosticMessage
    ) {}

    /**
     * Executes minimal preflight probes verifying model availability, structured JSON, and multimodal vision.
     *
     * @return PreflightResult
     */
    public PreflightResult executePreflightCheck() {
        if (strApiKey == null || strApiKey.isBlank()) {
            return new PreflightResult(
                    false, strModelName, "v1beta", false, false,
                    "GEMINI_API_KEY is not configured in environment"
            );
        }

        try {
            // 1. Minimal text + structured JSON probe
            boolean isJsonOk = probeStructuredJson();

            // 2. Multimodal image probe
            boolean isVisionOk = probeMultimodalVision();

            log.info("Gemini preflight validation passed for model [{}] (JSON: {}, Vision: {})",
                    strModelName, isJsonOk, isVisionOk);

            return new PreflightResult(true, strModelName, "v1beta", isJsonOk, isVisionOk, "Model operational");
        } catch (Exception exception) {
            log.warn("Gemini preflight probe failed for model [{}]: {}", strModelName, exception.getMessage());
            return new PreflightResult(false, strModelName, "v1beta", false, false, exception.getMessage());
        }
    }

    private boolean probeStructuredJson() throws Exception {
        String strUrl = "/v1beta/models/" + strModelName + ":generateContent";

        Map<String, Object> hmPayload = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", "Output JSON: {\"status\": \"OK\"}")))),
                "generationConfig", Map.of("response_mime_type", "application/json", "temperature", 0.0)
        );

        String strResponse = restClient.post()
                .uri(strUrl)
                .header("x-goog-api-key", strApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(hmPayload)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(strResponse);
        return root.path("candidates").isArray() && !root.path("candidates").isEmpty();
    }

    private boolean probeMultimodalVision() throws Exception {
        String strUrl = "/v1beta/models/" + strModelName + ":generateContent";

        Map<String, Object> hmInlineImg = Map.of("mime_type", "image/png", "data", TINY_PNG_BASE64);
        Map<String, Object> hmPayload = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("text", "Describe image in JSON: {\"color\": \"transparent\"}"),
                        Map.of("inline_data", hmInlineImg)
                ))),
                "generationConfig", Map.of("response_mime_type", "application/json")
        );

        String strResponse = restClient.post()
                .uri(strUrl)
                .header("x-goog-api-key", strApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(hmPayload)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(strResponse);
        return root.path("candidates").isArray() && !root.path("candidates").isEmpty();
    }
}
