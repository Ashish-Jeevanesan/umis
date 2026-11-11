package com.po.processor.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload requesting an audited modification of an extracted PO field.
 */
public record FieldUpdateRequestDto(
        @NotBlank(message = "Field name cannot be blank")
        String fieldName,
        String newValue,
        String reason
) {}
