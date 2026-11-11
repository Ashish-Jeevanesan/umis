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
 * Entity recording human workflow actions (verification, approval, rejection, reopening) on a PO.
 */
@Entity
@Table(name = "po_approval_history", schema = "po_processor")
public class PoApprovalHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private ExtractedPurchaseOrder purchaseOrder;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "action_by", nullable = false, length = 255)
    private String actionBy;

    @Column(name = "action_at", nullable = false, updatable = false)
    private Instant actionAt;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    public PoApprovalHistory() {
        this.id = UUID.randomUUID();
        this.actionAt = Instant.now();
    }

    public PoApprovalHistory(ExtractedPurchaseOrder po, String strAction, String strActionBy, String strComments) {
        this();
        this.purchaseOrder = po;
        this.action = strAction;
        this.actionBy = strActionBy;
        this.comments = strComments;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.actionAt == null) {
            this.actionAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ExtractedPurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(ExtractedPurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }
    public String getAction() { return action; }
    public void setAction(String strAction) { this.action = strAction; }
    public String getActionBy() { return actionBy; }
    public void setActionBy(String strActionBy) { this.actionBy = strActionBy; }
    public Instant getActionAt() { return actionAt; }
    public void setActionAt(Instant actionAt) { this.actionAt = actionAt; }
    public String getComments() { return comments; }
    public void setComments(String strComments) { this.comments = strComments; }
}
