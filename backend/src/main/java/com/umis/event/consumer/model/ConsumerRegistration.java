package com.umis.event.consumer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an external application registered to consume UMIS mailbox events.
 */
@Entity
@Table(
        name = "consumer_registrations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_consumer_tenant_code",
                columnNames = {"tenant_id", "consumer_code"}
        )
)
public class ConsumerRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId; // NULL indicates authorized global consumer

    @Column(name = "consumer_code", nullable = false, length = 64)
    private String consumerCode;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(length = 512)
    private String description;

    @Column(name = "subscribed_events", nullable = false, length = 512)
    private String subscribedEvents = "EMAIL_INGESTED";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ConsumerRegistration() {
    }

    public ConsumerRegistration(UUID tenantId, String consumerCode, String displayName, String description) {
        this.tenantId = tenantId;
        this.consumerCode = consumerCode;
        this.displayName = displayName;
        this.description = description;
        this.active = true;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getConsumerCode() {
        return consumerCode;
    }

    public void setConsumerCode(String consumerCode) {
        this.consumerCode = consumerCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSubscribedEvents() {
        return subscribedEvents;
    }

    public void setSubscribedEvents(String subscribedEvents) {
        this.subscribedEvents = subscribedEvents;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
