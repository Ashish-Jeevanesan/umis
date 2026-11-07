package com.umis.event.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Versioned canonical event contract for downstream distribution over RabbitMQ.
 *
 * @param <T> event payload type
 */
public record UmisEvent<T>(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        UUID tenantId,
        UUID mailboxId,
        T payload
) {
    public static <T> UmisEvent<T> of(String eventType, UUID tenantId, UUID mailboxId, T payload) {
        return new UmisEvent<>(
                UUID.randomUUID(),
                eventType,
                "1.0",
                Instant.now(),
                tenantId,
                mailboxId,
                payload
        );
    }
}
