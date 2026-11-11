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
 * Entity representing billing and invoicing information of an extracted Purchase Order.
 */
@Entity
@Table(name = "po_billing_details", schema = "po_processor")
public class PoBillingDetail {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private ExtractedPurchaseOrder purchaseOrder;

    @Column(name = "billing_entity", length = 255)
    private String billingEntity;

    @Column(name = "billing_contact", length = 255)
    private String billingContact;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_region", length = 100)
    private String stateRegion;

    @Column(name = "postal_code", length = 50)
    private String postalCode;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PoBillingDetail() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public PoBillingDetail(ExtractedPurchaseOrder purchaseOrder) {
        this();
        this.purchaseOrder = purchaseOrder;
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
    public String getBillingEntity() { return billingEntity; }
    public void setBillingEntity(String strBillingEntity) { this.billingEntity = strBillingEntity; }
    public String getBillingContact() { return billingContact; }
    public void setBillingContact(String strBillingContact) { this.billingContact = strBillingContact; }
    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String strBillingAddress) { this.billingAddress = strBillingAddress; }
    public String getCity() { return city; }
    public void setCity(String strCity) { this.city = strCity; }
    public String getStateRegion() { return stateRegion; }
    public void setStateRegion(String strStateRegion) { this.stateRegion = strStateRegion; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String strPostalCode) { this.postalCode = strPostalCode; }
    public String getCountry() { return country; }
    public void setCountry(String strCountry) { this.country = strCountry; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String strTaxId) { this.taxId = strTaxId; }
    public Instant getCreatedAt() { return createdAt; }
}
