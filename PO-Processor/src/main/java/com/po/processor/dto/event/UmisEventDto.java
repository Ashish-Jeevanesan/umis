package com.po.processor.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Mirror of the canonical UMIS event envelope.
 *
 * @param <T> event payload type
 */
public record UmisEventDto<T>(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        UUID tenantId,
        UUID mailboxId,
        T payload
) {}
