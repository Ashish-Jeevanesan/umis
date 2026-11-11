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
 * Entity representing an individual line item of an extracted Purchase Order.
 */
@Entity
@Table(name = "extracted_po_line_items", schema = "po_processor")
public class ExtractedPoLineItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private ExtractedPurchaseOrder purchaseOrder;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "product_code", length = 100)
    private String productCode;

    @Column(name = "mfg_part_number", length = 100)
    private String mfgPartNumber;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount", precision = 15, scale = 2)
    private BigDecimal discount;

    @Column(name = "tax_percentage", precision = 5, scale = 2)
    private BigDecimal taxPercentage;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "line_subtotal", precision = 15, scale = 2)
    private BigDecimal lineSubtotal;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "batch_lot_reference", length = 100)
    private String batchLotReference;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ExtractedPoLineItem() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
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
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String strProductCode) { this.productCode = strProductCode; }
    public String getMfgPartNumber() { return mfgPartNumber; }
    public void setMfgPartNumber(String strMfgPartNumber) { this.mfgPartNumber = strMfgPartNumber; }
    public String getProductName() { return productName; }
    public void setProductName(String strProductName) { this.productName = strProductName; }
    public String getDescription() { return description; }
    public void setDescription(String strDescription) { this.description = strDescription; }
    public String getCategory() { return category; }
    public void setCategory(String strCategory) { this.category = strCategory; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUnit() { return unit; }
    public void setUnit(String strUnit) { this.unit = strUnit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(BigDecimal taxPercentage) { this.taxPercentage = taxPercentage; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getLineSubtotal() { return lineSubtotal; }
    public void setLineSubtotal(BigDecimal lineSubtotal) { this.lineSubtotal = lineSubtotal; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    public String getCurrency() { return currency; }
    public void setCurrency(String strCurrency) { this.currency = strCurrency; }
    public String getBatchLotReference() { return batchLotReference; }
    public void setBatchLotReference(String strBatchLotReference) { this.batchLotReference = strBatchLotReference; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String strSerialNumber) { this.serialNumber = strSerialNumber; }
    public Instant getCreatedAt() { return createdAt; }
}
