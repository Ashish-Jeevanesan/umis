package com.umis.event.consumer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for registering a downstream consumer module.
 */
public record CreateConsumerRequest(
        UUID tenantId, // Optional: NULL means authorized global consumer

        @NotBlank(message = "Consumer code is required")
        @Size(min = 2, max = 64)
        String consumerCode,

        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 255)
        String displayName,

        String description,
        String subscribedEvents
) {}
