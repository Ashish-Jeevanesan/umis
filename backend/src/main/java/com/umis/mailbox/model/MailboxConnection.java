package com.umis.mailbox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Entity representing a connected customer email mailbox.
 */
@Entity
@Table(
        name = "mailbox_connections",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tenant_mailbox_email",
                columnNames = {"tenant_id", "email_address"}
        )
)
public class MailboxConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "email_address", nullable = false, length = 255)
    private String emailAddress;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private MailboxProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MailboxStatus status = MailboxStatus.CONNECTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_strategy", nullable = false, length = 32)
    private SyncStrategy syncStrategy = SyncStrategy.POLLING;

    @Column(name = "polling_interval_minutes", nullable = false)
    private int pollingIntervalMinutes = 5;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_successful_sync_at")
    private Instant lastSuccessfulSyncAt;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MailboxConnection() {
    }

    public MailboxConnection(
            UUID tenantId,
            String emailAddress,
            String displayName,
            MailboxProvider provider,
            SyncStrategy syncStrategy
    ) {
        this.tenantId = tenantId;
        this.emailAddress = emailAddress;
        this.displayName = displayName;
        this.provider = provider;
        this.syncStrategy = syncStrategy != null ? syncStrategy : SyncStrategy.POLLING;
        this.status = MailboxStatus.CONNECTED;
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

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public MailboxProvider getProvider() {
        return provider;
    }

    public void setProvider(MailboxProvider provider) {
        this.provider = provider;
    }

    public MailboxStatus getStatus() {
        return status;
    }

    public void setStatus(MailboxStatus status) {
        this.status = status;
    }

    public SyncStrategy getSyncStrategy() {
        return syncStrategy;
    }

    public void setSyncStrategy(SyncStrategy syncStrategy) {
        this.syncStrategy = syncStrategy;
    }

    public int getPollingIntervalMinutes() {
        return pollingIntervalMinutes;
    }

    public void setPollingIntervalMinutes(int pollingIntervalMinutes) {
        this.pollingIntervalMinutes = pollingIntervalMinutes;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public Instant getLastSuccessfulSyncAt() {
        return lastSuccessfulSyncAt;
    }

    public void setLastSuccessfulSyncAt(Instant lastSuccessfulSyncAt) {
        this.lastSuccessfulSyncAt = lastSuccessfulSyncAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
