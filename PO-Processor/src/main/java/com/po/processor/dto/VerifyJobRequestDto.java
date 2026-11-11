package com.po.processor.dto;

/**
 * Payload requesting verification of an extracted purchase order.
 */
public record VerifyJobRequestDto(
        String comments
) {}
