package com.po.processor.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing an AI-extracted Purchase Order header and review lifecycle state.
 */
@Entity
@Table(name = "extracted_purchase_orders", schema = "po_processor")
public class ExtractedPurchaseOrder {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "processing_job_id", nullable = false)
    private UUID processingJobId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @Column(name = "parent_po_id")
    private UUID parentPoId;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber = 1;

    @Column(name = "po_number", nullable = false, length = 100)
    private String poNumber;

    @Column(name = "po_title_description", length = 500)
    private String poTitleDescription;

    @Column(name = "po_date")
    private LocalDate poDate;

    @Column(name = "requested_delivery_date")
    private LocalDate requestedDeliveryDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "USD";

    @Column(name = "subtotal", precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount", precision = 15, scale = 2)
    private BigDecimal discount;

    @Column(name = "freight_charges", precision = 15, scale = 2)
    private BigDecimal freightCharges;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "tax_percentage", precision = 5, scale = 2)
    private BigDecimal taxPercentage;

    @Column(name = "grand_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "payment_terms", length = 255)
    private String paymentTerms;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "delivery_instructions", columnDefinition = "TEXT")
    private String deliveryInstructions;

    @Column(name = "incoterms", length = 50)
    private String incoterms;

    @Column(name = "contract_reference_number", length = 100)
    private String contractReferenceNumber;

    @Column(name = "quotation_reference", length = 100)
    private String quotationReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 50)
    private PoWorkflowStatus workflowStatus = PoWorkflowStatus.READY_FOR_REVIEW;

    @Column(name = "vendor_name", length = 255)
    private String vendorName;

    @Column(name = "vendor_address", columnDefinition = "TEXT")
    private String vendorAddress;

    @Column(name = "vendor_contact", length = 255)
    private String vendorContact;

    @Column(name = "vendor_email", length = 255)
    private String vendorEmail;

    @Column(name = "vendor_phone", length = 50)
    private String vendorPhone;

    @Column(name = "vendor_tax_id", length = 100)
    private String vendorTaxId;

    @Column(name = "vendor_account_number", length = 100)
    private String vendorAccountNumber;

    @Column(name = "is_approved", nullable = false)
    private boolean isApproved = false;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "verified_by", length = 255)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "is_superseded", nullable = false)
    private boolean isSuperseded = false;

    @Column(name = "superseded_by_id")
    private UUID supersededById;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ExtractedPoLineItem> lineItems = new ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PoPatientDetail patientDetail;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PoBillingDetail billingDetail;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PoShippingDetail shippingDetail;

    public ExtractedPurchaseOrder() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Adds a line item to this purchase order.
     *
     * @param lineItem Line item to add
     */
    public void addLineItem(ExtractedPoLineItem lineItem) {
        this.lineItems.add(lineItem);
        lineItem.setPurchaseOrder(this);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProcessingJobId() { return processingJobId; }
    public void setProcessingJobId(UUID processingJobId) { this.processingJobId = processingJobId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public Hospital getHospital() { return hospital; }
    public void setHospital(Hospital hospital) { this.hospital = hospital; }
    public UUID getParentPoId() { return parentPoId; }
    public void setParentPoId(UUID parentPoId) { this.parentPoId = parentPoId; }
    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int revisionNumber) { this.revisionNumber = revisionNumber; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String strPoNumber) { this.poNumber = strPoNumber; }
    public String getPoTitleDescription() { return poTitleDescription; }
    public void setPoTitleDescription(String strPoTitleDescription) { this.poTitleDescription = strPoTitleDescription; }
    public LocalDate getPoDate() { return poDate; }
    public void setPoDate(LocalDate poDate) { this.poDate = poDate; }
    public LocalDate getRequestedDeliveryDate() { return requestedDeliveryDate; }
    public void setRequestedDeliveryDate(LocalDate requestedDeliveryDate) { this.requestedDeliveryDate = requestedDeliveryDate; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String strCurrency) { this.currency = strCurrency; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getFreightCharges() { return freightCharges; }
    public void setFreightCharges(BigDecimal freightCharges) { this.freightCharges = freightCharges; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public void setTaxPercentage(BigDecimal taxPercentage) { this.taxPercentage = taxPercentage; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String strPaymentTerms) { this.paymentTerms = strPaymentTerms; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String strPaymentMethod) { this.paymentMethod = strPaymentMethod; }
    public String getDeliveryInstructions() { return deliveryInstructions; }
    public void setDeliveryInstructions(String strDeliveryInstructions) { this.deliveryInstructions = strDeliveryInstructions; }
    public String getIncoterms() { return incoterms; }
    public void setIncoterms(String strIncoterms) { this.incoterms = strIncoterms; }
    public String getContractReferenceNumber() { return contractReferenceNumber; }
    public void setContractReferenceNumber(String strContractReferenceNumber) { this.contractReferenceNumber = strContractReferenceNumber; }
    public String getQuotationReference() { return quotationReference; }
    public void setQuotationReference(String strQuotationReference) { this.quotationReference = strQuotationReference; }
    public PoWorkflowStatus getWorkflowStatus() { return workflowStatus; }
    public void setWorkflowStatus(PoWorkflowStatus workflowStatus) { this.workflowStatus = workflowStatus; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String strVendorName) { this.vendorName = strVendorName; }
    public String getVendorAddress() { return vendorAddress; }
    public void setVendorAddress(String strVendorAddress) { this.vendorAddress = strVendorAddress; }
    public String getVendorContact() { return vendorContact; }
    public void setVendorContact(String strVendorContact) { this.vendorContact = strVendorContact; }
    public String getVendorEmail() { return vendorEmail; }
    public void setVendorEmail(String strVendorEmail) { this.vendorEmail = strVendorEmail; }
    public String getVendorPhone() { return vendorPhone; }
    public void setVendorPhone(String strVendorPhone) { this.vendorPhone = strVendorPhone; }
    public String getVendorTaxId() { return vendorTaxId; }
    public void setVendorTaxId(String strVendorTaxId) { this.vendorTaxId = strVendorTaxId; }
    public String getVendorAccountNumber() { return vendorAccountNumber; }
    public void setVendorAccountNumber(String strVendorAccountNumber) { this.vendorAccountNumber = strVendorAccountNumber; }
    public boolean isApproved() { return isApproved; }
    public void setApproved(boolean isApproved) { this.isApproved = isApproved; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String strApprovedBy) { this.approvedBy = strApprovedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String strVerifiedBy) { this.verifiedBy = strVerifiedBy; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ExtractedPoLineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<ExtractedPoLineItem> alLineItems) { this.lineItems = alLineItems; }
    public PoPatientDetail getPatientDetail() { return patientDetail; }
    public void setPatientDetail(PoPatientDetail patientDetail) { this.patientDetail = patientDetail; }
    public PoBillingDetail getBillingDetail() { return billingDetail; }
    public void setBillingDetail(PoBillingDetail billingDetail) { this.billingDetail = billingDetail; }
    public PoShippingDetail getShippingDetail() { return shippingDetail; }
    public void setShippingDetail(PoShippingDetail shippingDetail) { this.shippingDetail = shippingDetail; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public boolean isSuperseded() { return isSuperseded; }
    public void setSuperseded(boolean isSuperseded) { this.isSuperseded = isSuperseded; }
    public UUID getSupersededById() { return supersededById; }
    public void setSupersededById(UUID supersededById) { this.supersededById = supersededById; }
    public Instant getSupersededAt() { return supersededAt; }
    public void setSupersededAt(Instant supersededAt) { this.supersededAt = supersededAt; }
}
