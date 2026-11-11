package com.po.processor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity storing comparison findings and field discrepancies between email body and attachments.
 */
@Entity
@Table(name = "po_body_attachment_comparisons", schema = "po_processor")
public class PoBodyAttachmentComparison {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private ExtractedPurchaseOrder purchaseOrder;

    @Column(name = "processing_job_id", nullable = false)
    private UUID processingJobId;

    @Column(name = "comparison_status", nullable = false, length = 50)
    private String comparisonStatus;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "comparison_report", nullable = false, columnDefinition = "JSONB")
    private String comparisonReport;

    @Column(name = "conflict_count", nullable = false)
    private int conflictCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PoBodyAttachmentComparison() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public PoBodyAttachmentComparison(
            ExtractedPurchaseOrder po,
            UUID processingJobId,
            String strStatus,
            String strReportJson,
            int conflictCount
    ) {
        this();
        this.purchaseOrder = po;
        this.processingJobId = processingJobId;
        this.comparisonStatus = strStatus;
        this.comparisonReport = strReportJson;
        this.conflictCount = conflictCount;
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
    public ExtractedPurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(ExtractedPurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }
    public UUID getProcessingJobId() { return processingJobId; }
    public void setProcessingJobId(UUID processingJobId) { this.processingJobId = processingJobId; }
    public String getComparisonStatus() { return comparisonStatus; }
    public void setComparisonStatus(String strComparisonStatus) { this.comparisonStatus = strComparisonStatus; }
    public String getComparisonReport() { return comparisonReport; }
    public void setComparisonReport(String strComparisonReport) { this.comparisonReport = strComparisonReport; }
    public int getConflictCount() { return conflictCount; }
    public void setConflictCount(int conflictCount) { this.conflictCount = conflictCount; }
    public Instant getCreatedAt() { return createdAt; }
}
