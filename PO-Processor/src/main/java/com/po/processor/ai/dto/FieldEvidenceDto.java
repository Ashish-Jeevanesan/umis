package com.po.processor.ai.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data transfer object capturing field-level extraction evidence, source location, and confidence.
 */
public record FieldEvidenceDto(
        String fieldName,
        String extractedValue,
        String normalizedValue,
        BigDecimal confidenceScore,
        String sourceType,
        UUID sourceAttachmentId,
        String sourceFileName,
        Integer sourcePage,
        String evidenceText,
        boolean isInferred
) {}
