package com.umis.event.publisher;

import com.umis.config.UmisProperties;
import com.umis.event.model.EventOutbox;
import com.umis.event.model.OutboxStatus;
import com.umis.event.repository.EventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled background relay process implementing the Transactional Outbox pattern.
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final EventOutboxRepository outboxRepository;
    private final EventPublisher eventPublisher;
    private final UmisProperties properties;

    public OutboxRelayScheduler(
            EventOutboxRepository outboxRepository,
            EventPublisher eventPublisher,
            UmisProperties properties
    ) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relayPendingEvents() {
        List<EventOutbox> pendingEvents = outboxRepository.findByStatusOrderByOccurredAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, 50));

        if (pendingEvents.isEmpty()) {
            return;
        }

        int maxRetries = properties.getSync().getMaxRetryAttempts();

        for (EventOutbox outbox : pendingEvents) {
            try {
                eventPublisher.publish(outbox.getEventType(), outbox.getPayload());
                outbox.setStatus(OutboxStatus.PUBLISHED);
                outbox.setPublishedAt(Instant.now());
                outbox.setLastError(null);
            } catch (Exception exception) {
                log.warn("Failed to publish outbox event {}: {}", outbox.getEventId(), exception.getMessage());
                outbox.setRetryCount(outbox.getRetryCount() + 1);
                outbox.setLastError(exception.getMessage());
                if (outbox.getRetryCount() >= maxRetries) {
                    outbox.setStatus(OutboxStatus.FAILED);
                }
            }
            outboxRepository.save(outbox);
        }
    }
}
