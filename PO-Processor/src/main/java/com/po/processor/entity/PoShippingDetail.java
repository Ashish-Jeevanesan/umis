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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity representing shipping destination and logistics delivery instructions for a PO.
 */
@Entity
@Table(name = "po_shipping_details", schema = "po_processor")
public class PoShippingDetail {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private ExtractedPurchaseOrder purchaseOrder;

    @Column(name = "ship_to_entity", length = 255)
    private String shipToEntity;

    @Column(name = "hospital_location_name", length = 255)
    private String hospitalLocationName;

    @Column(name = "shipping_contact", length = 255)
    private String shippingContact;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_region", length = 100)
    private String stateRegion;

    @Column(name = "postal_code", length = 50)
    private String postalCode;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "delivery_instructions", columnDefinition = "TEXT")
    private String deliveryInstructions;

    @Column(name = "requested_delivery_date")
    private LocalDate requestedDeliveryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PoShippingDetail() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public PoShippingDetail(ExtractedPurchaseOrder purchaseOrder) {
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
    public String getShipToEntity() { return shipToEntity; }
    public void setShipToEntity(String strShipToEntity) { this.shipToEntity = strShipToEntity; }
    public String getHospitalLocationName() { return hospitalLocationName; }
    public void setHospitalLocationName(String strHospitalLocationName) { this.hospitalLocationName = strHospitalLocationName; }
    public String getShippingContact() { return shippingContact; }
    public void setShippingContact(String strShippingContact) { this.shippingContact = strShippingContact; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String strShippingAddress) { this.shippingAddress = strShippingAddress; }
    public String getCity() { return city; }
    public void setCity(String strCity) { this.city = strCity; }
    public String getStateRegion() { return stateRegion; }
    public void setStateRegion(String strStateRegion) { this.stateRegion = strStateRegion; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String strPostalCode) { this.postalCode = strPostalCode; }
    public String getCountry() { return country; }
    public void setCountry(String strCountry) { this.country = strCountry; }
    public String getDeliveryInstructions() { return deliveryInstructions; }
    public void setDeliveryInstructions(String strDeliveryInstructions) { this.deliveryInstructions = strDeliveryInstructions; }
    public LocalDate getRequestedDeliveryDate() { return requestedDeliveryDate; }
    public void setRequestedDeliveryDate(LocalDate requestedDeliveryDate) { this.requestedDeliveryDate = requestedDeliveryDate; }
    public Instant getCreatedAt() { return createdAt; }
}
