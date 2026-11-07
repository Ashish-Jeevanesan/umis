package com.po.processor.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a PO processing job for an email received from UMIS.
 */
@Entity
@Table(name = "processing_jobs", schema = "po_processor")
public class ProcessingJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "umis_email_id", nullable = false)
    private UUID umisEmailId;

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 50)
    private JobProcessingStatus processingStatus;

    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "processingJob", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProcessingJobAttachment> attachments = new ArrayList<>();

    public ProcessingJob() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ProcessingJob(UUID tenantId, UUID umisEmailId, UUID mailboxId, UUID eventId, Instant receivedAt) {
        this();
        this.tenantId = tenantId;
        this.umisEmailId = umisEmailId;
        this.mailboxId = mailboxId;
        this.eventId = eventId;
        this.receivedAt = receivedAt != null ? receivedAt : Instant.now();
        this.processingStatus = JobProcessingStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addAttachment(ProcessingJobAttachment attachment) {
        this.attachments.add(attachment);
        attachment.setProcessingJob(this);
    }

    // Getters and Setters
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

    public UUID getUmisEmailId() {
        return umisEmailId;
    }

    public void setUmisEmailId(UUID umisEmailId) {
        this.umisEmailId = umisEmailId;
    }

    public UUID getMailboxId() {
        return mailboxId;
    }

    public void setMailboxId(UUID mailboxId) {
        this.mailboxId = mailboxId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public JobProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(JobProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public int getProcessingAttempts() {
        return processingAttempts;
    }

    public void setProcessingAttempts(int processingAttempts) {
        this.processingAttempts = processingAttempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ProcessingJobAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ProcessingJobAttachment> attachments) {
        this.attachments = attachments;
    }
}
