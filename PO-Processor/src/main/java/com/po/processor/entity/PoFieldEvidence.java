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
 * Entity representing field-level extraction provenance, confidence scores, and source evidence snippets.
 */
@Entity
@Table(name = "po_field_evidence", schema = "po_processor")
public class PoFieldEvidence {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private ExtractedPurchaseOrder purchaseOrder;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "extracted_value", columnDefinition = "TEXT")
    private String extractedValue;

    @Column(name = "normalized_value", columnDefinition = "TEXT")
    private String normalizedValue;

    @Column(name = "confidence_score", precision = 4, scale = 3)
    private BigDecimal confidenceScore;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_attachment_id")
    private UUID sourceAttachmentId;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "evidence_text", columnDefinition = "TEXT")
    private String evidenceText;

    @Column(name = "is_inferred", nullable = false)
    private boolean isInferred = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PoFieldEvidence() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public PoFieldEvidence(ExtractedPurchaseOrder po, String strFieldName, String strExtractedValue, BigDecimal score) {
        this();
        this.purchaseOrder = po;
        this.fieldName = strFieldName;
        this.extractedValue = strExtractedValue;
        this.confidenceScore = score;
        this.sourceType = "ATTACHMENT";
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
    public String getFieldName() { return fieldName; }
    public void setFieldName(String strFieldName) { this.fieldName = strFieldName; }
    public String getExtractedValue() { return extractedValue; }
    public void setExtractedValue(String strExtractedValue) { this.extractedValue = strExtractedValue; }
    public String getNormalizedValue() { return normalizedValue; }
    public void setNormalizedValue(String strNormalizedValue) { this.normalizedValue = strNormalizedValue; }
    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String strSourceType) { this.sourceType = strSourceType; }
    public UUID getSourceAttachmentId() { return sourceAttachmentId; }
    public void setSourceAttachmentId(UUID sourceAttachmentId) { this.sourceAttachmentId = sourceAttachmentId; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String strSourceFileName) { this.sourceFileName = strSourceFileName; }
    public Integer getSourcePage() { return sourcePage; }
    public void setSourcePage(Integer sourcePage) { this.sourcePage = sourcePage; }
    public String getEvidenceText() { return evidenceText; }
    public void setEvidenceText(String strEvidenceText) { this.evidenceText = strEvidenceText; }
    public boolean isInferred() { return isInferred; }
    public void setInferred(boolean inferred) { isInferred = inferred; }
    public Instant getCreatedAt() { return createdAt; }
}
