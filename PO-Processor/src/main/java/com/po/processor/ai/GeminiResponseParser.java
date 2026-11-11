package com.po.processor.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates and maps raw Gemini response JSON into ExtractedPurchaseOrderDto.
 */
@Component
public class GeminiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(GeminiResponseParser.class);
    private final ObjectMapper objectMapper;

    public GeminiResponseParser() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Parses the Gemini API response string into a typed ExtractedPurchaseOrderDto.
     *
     * @param strRawResponse Raw response payload from Gemini Generative Language API
     * @return ExtractedPurchaseOrderDto
     */
    public ExtractedPurchaseOrderDto parseResponse(String strRawResponse) {
        return parseWithTelemetry(strRawResponse).purchaseOrderDto();
    }

    /**
     * Parses the Gemini API response string with full token telemetry.
     *
     * @param strRawResponse Raw response payload from Gemini Generative Language API
     * @return GeminiExtractionTelemetryDto
     */
    public com.po.processor.ai.dto.GeminiExtractionTelemetryDto parseWithTelemetry(String strRawResponse) {
        if (strRawResponse == null || strRawResponse.isBlank()) {
            throw new IllegalArgumentException("Empty response received from Gemini API");
        }

        try {
            String strTextToParse = cleanJsonFences(strRawResponse);
            int inputTokens = 0;
            int outputTokens = 0;
            String finishReason = "STOP";

            if (strTextToParse.startsWith("{") && strTextToParse.contains("\"candidates\"")) {
                JsonNode rootNode = objectMapper.readTree(strTextToParse);
                checkCandidateFinishReason(rootNode);

                JsonNode usageNode = rootNode.path("usageMetadata");
                inputTokens = usageNode.path("promptTokenCount").asInt(0);
                outputTokens = usageNode.path("candidatesTokenCount").asInt(0);

                JsonNode candNode = rootNode.path("candidates");
                if (candNode.isArray() && !candNode.isEmpty()) {
                    finishReason = candNode.get(0).path("finishReason").asText("STOP");
                }

                strTextToParse = cleanJsonFences(extractTextContent(rootNode));
            }

            ExtractedPurchaseOrderDto dto = objectMapper.readValue(strTextToParse, ExtractedPurchaseOrderDto.class);
            return new com.po.processor.ai.dto.GeminiExtractionTelemetryDto(
                    dto, inputTokens, outputTokens, finishReason, strRawResponse
            );
        } catch (com.po.processor.exception.GeminiResponseTruncatedException truncatedEx) {
            throw truncatedEx;
        } catch (com.fasterxml.jackson.core.JsonParseException | com.fasterxml.jackson.databind.JsonMappingException jsonEx) {
            log.error("Incomplete or truncated JSON received from Gemini: {}", jsonEx.getMessage());
            throw new com.po.processor.exception.GeminiResponseTruncatedException(
                    "Incomplete or truncated JSON response from Gemini: " + jsonEx.getMessage(),
                    "INCOMPLETE_JSON"
            );
        } catch (Exception exception) {
            log.error("Failed parsing structured Gemini response JSON", exception);
            throw new IllegalStateException("Failed to parse Gemini response: " + exception.getMessage(), exception);
        }
    }

    private void checkCandidateFinishReason(JsonNode rootNode) {
        JsonNode candidatesNode = rootNode.path("candidates");
        if (candidatesNode.isArray() && !candidatesNode.isEmpty()) {
            JsonNode candidate = candidatesNode.get(0);
            String finishReason = candidate.path("finishReason").asText(null);
            if ("MAX_TOKENS".equalsIgnoreCase(finishReason)) {
                throw new com.po.processor.exception.GeminiResponseTruncatedException(
                        "Gemini extraction response was truncated: MAX_TOKENS limit reached",
                        "MAX_TOKENS"
                );
            }
        }
    }

    private String extractTextContent(JsonNode rootNode) {
        // If the response is the direct Gemini API candidate structure
        JsonNode candidatesNode = rootNode.path("candidates");
        if (candidatesNode.isArray() && !candidatesNode.isEmpty()) {
            JsonNode partsNode = candidatesNode.get(0).path("content").path("parts");
            if (partsNode.isArray() && !partsNode.isEmpty()) {
                JsonNode textNode = partsNode.get(0).path("text");
                if (!textNode.isMissingNode()) {
                    return textNode.asText();
                }
            }
        }
        // Fallback: direct string or raw JSON
        return rootNode.isTextual() ? rootNode.asText() : rootNode.toString();
    }

    private String cleanJsonFences(String strInput) {
        String strTrimmed = strInput.trim();
        if (strTrimmed.startsWith("```json")) {
            strTrimmed = strTrimmed.substring(7);
        } else if (strTrimmed.startsWith("```")) {
            strTrimmed = strTrimmed.substring(3);
        }
        if (strTrimmed.endsWith("```")) {
            strTrimmed = strTrimmed.substring(0, strTrimmed.length() - 3);
        }
        return strTrimmed.trim();
    }
}
