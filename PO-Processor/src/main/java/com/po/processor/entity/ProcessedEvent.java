package com.po.processor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for tracking processed events to guarantee idempotency.
 */
@Entity
@Table(
        name = "processed_events",
        schema = "po_processor",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pe_event_consumer", columnNames = {"event_id", "consumer_name"})
        }
)
public class ProcessedEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedEvent() {
        this.id = UUID.randomUUID();
        this.processedAt = Instant.now();
    }

    public ProcessedEvent(UUID eventId, String consumerName) {
        this();
        this.eventId = eventId;
        this.consumerName = consumerName;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.processedAt == null) {
            this.processedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
