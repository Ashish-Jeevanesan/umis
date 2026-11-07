package com.umis.event.consumer.model;

import com.umis.email.model.EmailMessage;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity tracking independent downstream processing states per consumer module.
 */
@Entity
@Table(
        name = "email_processing_status",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_email_consumer",
                columnNames = {"email_id", "consumer_code"}
        )
)
public class EmailProcessingStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    private EmailMessage email;

    @Column(name = "consumer_code", nullable = false, length = 64)
    private String consumerCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingStatus status = ProcessingStatus.PENDING;

    @Column(name = "reference_id", length = 255)
    private String referenceId;

    @Column(length = 2000)
    private String message;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "acknowledgement_idempotency_key", length = 128)
    private String acknowledgementIdempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EmailProcessingStatus() {
    }

    public EmailProcessingStatus(EmailMessage email, String consumerCode, ProcessingStatus status) {
        this.email = email;
        this.consumerCode = consumerCode;
        this.status = status != null ? status : ProcessingStatus.PENDING;
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

    public EmailMessage getEmail() {
        return email;
    }

    public String getConsumerCode() {
        return consumerCode;
    }

    public ProcessingStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessingStatus status) {
        this.status = status;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getAcknowledgementIdempotencyKey() {
        return acknowledgementIdempotencyKey;
    }

    public void setAcknowledgementIdempotencyKey(String acknowledgementIdempotencyKey) {
        this.acknowledgementIdempotencyKey = acknowledgementIdempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
