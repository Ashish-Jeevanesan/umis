package com.po.processor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity tracking duplicate and revision detection results for an incoming processing job.
 */
@Entity
@Table(name = "po_duplicate_detections", schema = "po_processor")
public class PoDuplicateDetection {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "processing_job_id", nullable = false)
    private UUID processingJobId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_po_id")
    private ExtractedPurchaseOrder originalPurchaseOrder;

    @Column(name = "duplicate_type", nullable = false, length = 50)
    private String duplicateType;

    @Column(name = "detection_reason", nullable = false, columnDefinition = "TEXT")
    private String detectionReason;

    @Column(name = "confidence_score", precision = 4, scale = 3)
    private BigDecimal confidenceScore;

    @Column(name = "review_status", nullable = false, length = 50)
    private String reviewStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PoDuplicateDetection() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public PoDuplicateDetection(
            UUID processingJobId,
            ExtractedPurchaseOrder originalPo,
            String strDuplicateType,
            String strReason,
            BigDecimal score,
            String strReviewStatus
    ) {
        this();
        this.processingJobId = processingJobId;
        this.originalPurchaseOrder = originalPo;
        this.duplicateType = strDuplicateType;
        this.detectionReason = strReason;
        this.confidenceScore = score;
        this.reviewStatus = strReviewStatus;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProcessingJobId() { return processingJobId; }
    public void setProcessingJobId(UUID processingJobId) { this.processingJobId = processingJobId; }
    public ExtractedPurchaseOrder getOriginalPurchaseOrder() { return originalPurchaseOrder; }
    public void setOriginalPurchaseOrder(ExtractedPurchaseOrder originalPurchaseOrder) { this.originalPurchaseOrder = originalPurchaseOrder; }
    public String getDuplicateType() { return duplicateType; }
    public void setDuplicateType(String strDuplicateType) { this.duplicateType = strDuplicateType; }
    public String getDetectionReason() { return detectionReason; }
    public void setDetectionReason(String strDetectionReason) { this.detectionReason = strDetectionReason; }
    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String strReviewStatus) { this.reviewStatus = strReviewStatus; }
    public Instant getCreatedAt() { return createdAt; }
}
