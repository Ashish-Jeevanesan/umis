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
 * Entity representing an individual validation rule execution result on an extracted PO.
 */
@Entity
@Table(name = "po_validation_results", schema = "po_processor")
public class PoValidationResult {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private ExtractedPurchaseOrder purchaseOrder;

    @Column(name = "rule_code", nullable = false, length = 100)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Column(name = "expected_value", columnDefinition = "TEXT")
    private String expectedValue;

    @Column(name = "actual_value", columnDefinition = "TEXT")
    private String actualValue;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PoValidationResult() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public PoValidationResult(
            ExtractedPurchaseOrder po,
            String strRuleCode,
            String strRuleName,
            String strSeverity,
            boolean passed,
            String strExplanation
    ) {
        this();
        this.purchaseOrder = po;
        this.ruleCode = strRuleCode;
        this.ruleName = strRuleName;
        this.severity = strSeverity;
        this.passed = passed;
        this.explanation = strExplanation;
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
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String strRuleCode) { this.ruleCode = strRuleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String strRuleName) { this.ruleName = strRuleName; }
    public String getSeverity() { return severity; }
    public void setSeverity(String strSeverity) { this.severity = strSeverity; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String strExpectedValue) { this.expectedValue = strExpectedValue; }
    public String getActualValue() { return actualValue; }
    public void setActualValue(String strActualValue) { this.actualValue = strActualValue; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String strExplanation) { this.explanation = strExplanation; }
    public Instant getCreatedAt() { return createdAt; }
}
