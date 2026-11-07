package com.umis.email.model;

import com.umis.mailbox.model.MailboxConnection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity tracking an asynchronous ingestion job execution batch.
 */
@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mailbox_id", nullable = false)
    private MailboxConnection mailbox;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private TriggerType triggerType = TriggerType.POLLING;

    @Column(nullable = false, length = 32)
    private String status = "RUNNING";

    @Column(name = "items_discovered", nullable = false)
    private int itemsDiscovered = 0;

    @Column(name = "items_ingested", nullable = false)
    private int itemsIngested = 0;

    @Column(name = "items_skipped", nullable = false)
    private int itemsSkipped = 0;

    @Column(name = "items_failed", nullable = false)
    private int itemsFailed = 0;

    @Column(name = "error_summary", length = 2000)
    private String errorSummary;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public IngestionJob() {
    }

    public IngestionJob(UUID tenantId, MailboxConnection mailbox, TriggerType triggerType) {
        this.tenantId = tenantId;
        this.mailbox = mailbox;
        this.triggerType = triggerType;
        this.status = "RUNNING";
        this.startedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public MailboxConnection getMailbox() {
        return mailbox;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getItemsDiscovered() {
        return itemsDiscovered;
    }

    public void setItemsDiscovered(int itemsDiscovered) {
        this.itemsDiscovered = itemsDiscovered;
    }

    public int getItemsIngested() {
        return itemsIngested;
    }

    public void setItemsIngested(int itemsIngested) {
        this.itemsIngested = itemsIngested;
    }

    public int getItemsSkipped() {
        return itemsSkipped;
    }

    public void setItemsSkipped(int itemsSkipped) {
        this.itemsSkipped = itemsSkipped;
    }

    public int getItemsFailed() {
        return itemsFailed;
    }

    public void setItemsFailed(int itemsFailed) {
        this.itemsFailed = itemsFailed;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
