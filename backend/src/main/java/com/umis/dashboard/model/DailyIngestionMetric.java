package com.umis.dashboard.model;

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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pre-aggregated daily metrics entity for fast dashboard reporting without full table scans.
 */
@Entity
@Table(
        name = "daily_ingestion_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_metric_tenant_mailbox_date",
                columnNames = {"metric_date", "tenant_id", "mailbox_id", "provider"}
        )
)
public class DailyIngestionMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "mailbox_id")
    private UUID mailboxId;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(name = "emails_discovered", nullable = false)
    private int emailsDiscovered = 0;

    @Column(name = "emails_picked", nullable = false)
    private int emailsPicked = 0;

    @Column(name = "emails_ingested", nullable = false)
    private int emailsIngested = 0;

    @Column(name = "emails_published", nullable = false)
    private int emailsPublished = 0;

    @Column(name = "emails_parsed_success", nullable = false)
    private int emailsParsedSuccess = 0;

    @Column(name = "emails_failed", nullable = false)
    private int emailsFailed = 0;

    @Column(name = "duplicate_emails", nullable = false)
    private int duplicateEmails = 0;

    @Column(name = "total_attachment_bytes", nullable = false)
    private long totalAttachmentBytes = 0;

    @Column(name = "avg_ingestion_latency_ms", nullable = false)
    private long avgIngestionLatencyMs = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DailyIngestionMetric() {
    }

    public DailyIngestionMetric(LocalDate metricDate, UUID tenantId, UUID mailboxId, String provider) {
        this.metricDate = metricDate;
        this.tenantId = tenantId;
        this.mailboxId = mailboxId;
        this.provider = provider;
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

    public LocalDate getMetricDate() {
        return metricDate;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getMailboxId() {
        return mailboxId;
    }

    public String getProvider() {
        return provider;
    }

    public int getEmailsDiscovered() {
        return emailsDiscovered;
    }

    public void setEmailsDiscovered(int emailsDiscovered) {
        this.emailsDiscovered = emailsDiscovered;
    }

    public int getEmailsPicked() {
        return emailsPicked;
    }

    public void setEmailsPicked(int emailsPicked) {
        this.emailsPicked = emailsPicked;
    }

    public int getEmailsIngested() {
        return emailsIngested;
    }

    public void setEmailsIngested(int emailsIngested) {
        this.emailsIngested = emailsIngested;
    }

    public int getEmailsPublished() {
        return emailsPublished;
    }

    public void setEmailsPublished(int emailsPublished) {
        this.emailsPublished = emailsPublished;
    }

    public int getEmailsParsedSuccess() {
        return emailsParsedSuccess;
    }

    public void setEmailsParsedSuccess(int emailsParsedSuccess) {
        this.emailsParsedSuccess = emailsParsedSuccess;
    }

    public int getEmailsFailed() {
        return emailsFailed;
    }

    public void setEmailsFailed(int emailsFailed) {
        this.emailsFailed = emailsFailed;
    }

    public int getDuplicateEmails() {
        return duplicateEmails;
    }

    public void setDuplicateEmails(int duplicateEmails) {
        this.duplicateEmails = duplicateEmails;
    }

    public long getTotalAttachmentBytes() {
        return totalAttachmentBytes;
    }

    public void setTotalAttachmentBytes(long totalAttachmentBytes) {
        this.totalAttachmentBytes = totalAttachmentBytes;
    }

    public long getAvgIngestionLatencyMs() {
        return avgIngestionLatencyMs;
    }

    public void setAvgIngestionLatencyMs(long avgIngestionLatencyMs) {
        this.avgIngestionLatencyMs = avgIngestionLatencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
