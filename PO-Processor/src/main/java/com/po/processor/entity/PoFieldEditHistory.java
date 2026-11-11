package com.po.processor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity capturing the immutable audit trail of human edits made to extracted PO fields.
 */
@Entity
@Table(name = "po_field_edit_history", schema = "po_processor")
public class PoFieldEditHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private ExtractedPurchaseOrder purchaseOrder;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "original_value", columnDefinition = "TEXT")
    private String originalValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "edited_by", nullable = false, length = 255)
    private String editedBy;

    @Column(name = "edited_at", nullable = false, updatable = false)
    private Instant editedAt;

    @Column(name = "edit_reason", columnDefinition = "TEXT")
    private String editReason;

    public PoFieldEditHistory() {
        this.id = UUID.randomUUID();
        this.editedAt = Instant.now();
    }

    public PoFieldEditHistory(
            ExtractedPurchaseOrder po,
            String strFieldName,
            String strOriginalValue,
            String strNewValue,
            String strEditedBy,
            String strReason
    ) {
        this();
        this.purchaseOrder = po;
        this.fieldName = strFieldName;
        this.originalValue = strOriginalValue;
        this.newValue = strNewValue;
        this.editedBy = strEditedBy;
        this.editReason = strReason;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.editedAt == null) {
            this.editedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ExtractedPurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(ExtractedPurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String strFieldName) { this.fieldName = strFieldName; }
    public String getOriginalValue() { return originalValue; }
    public void setOriginalValue(String strOriginalValue) { this.originalValue = strOriginalValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String strNewValue) { this.newValue = strNewValue; }
    public String getEditedBy() { return editedBy; }
    public void setEditedBy(String strEditedBy) { this.editedBy = strEditedBy; }
    public Instant getEditedAt() { return editedAt; }
    public void setEditedAt(Instant editedAt) { this.editedAt = editedAt; }
    public String getEditReason() { return editReason; }
    public void setEditReason(String strEditReason) { this.editReason = strEditReason; }
}
