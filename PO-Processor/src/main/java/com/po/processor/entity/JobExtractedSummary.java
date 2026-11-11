package com.po.processor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Embeddable representing high-level PO summary metadata cached on the processing job.
 */
@Embeddable
public class JobExtractedSummary {

    @Column(name = "po_number", length = 100)
    private String poNumber;

    @Column(name = "hospital_name", length = 255)
    private String hospitalName;

    @Column(name = "vendor_name", length = 255)
    private String vendorName;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "extraction_method", length = 50)
    private String extractionMethod;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

    public JobExtractedSummary() {
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String strPoNumber) {
        this.poNumber = strPoNumber;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String strHospitalName) {
        this.hospitalName = strHospitalName;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String strVendorName) {
        this.vendorName = strVendorName;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String strCurrency) {
        this.currency = strCurrency;
    }

    public String getExtractionMethod() {
        return extractionMethod;
    }

    public void setExtractionMethod(String strExtractionMethod) {
        this.extractionMethod = strExtractionMethod;
    }

    public UUID getDuplicateOfId() {
        return duplicateOfId;
    }

    public void setDuplicateOfId(UUID duplicateOfId) {
        this.duplicateOfId = duplicateOfId;
    }
}
