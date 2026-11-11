package com.po.processor.dto;

/**
 * Payload requesting final human approval of a verified purchase order.
 */
public record ApproveJobRequestDto(
        String comments
) {}
