package com.umis.event.consumer.dto;

import com.umis.event.consumer.model.ConsumerRegistration;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object for external downstream consumer applications.
 */
public record ConsumerRegistrationDto(
        UUID id,
        UUID tenantId,
        String consumerCode,
        String displayName,
        String description,
        String subscribedEvents,
        boolean active,
        Instant createdAt
) {
    public static ConsumerRegistrationDto from(ConsumerRegistration entity) {
        return new ConsumerRegistrationDto(
                entity.getId(),
                entity.getTenantId(),
                entity.getConsumerCode(),
                entity.getDisplayName(),
                entity.getDescription(),
                entity.getSubscribedEvents(),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }
}
