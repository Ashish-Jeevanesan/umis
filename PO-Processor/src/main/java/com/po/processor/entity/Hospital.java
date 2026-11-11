package com.po.processor.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a canonical customer / hospital master account.
 */
@Entity
@Table(name = "hospitals", schema = "po_processor")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Hospital {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "hospital_code", length = 100)
    private String hospitalCode;

    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_region", length = 100)
    private String stateRegion;

    @Column(name = "postal_code", length = 50)
    private String postalCode;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "hospital", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HospitalAlias> aliases = new ArrayList<>();

    public Hospital() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Hospital(UUID tenantId, String strNormalizedName) {
        this();
        this.tenantId = tenantId;
        this.normalizedName = strNormalizedName;
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
     * Adds a recognized alias to this canonical hospital record.
     *
     * @param alias HospitalAlias to attach
     */
    public void addAlias(HospitalAlias alias) {
        this.aliases.add(alias);
        alias.setHospital(this);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String strNormalizedName) {
        this.normalizedName = strNormalizedName;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String strLegalName) {
        this.legalName = strLegalName;
    }

    public String getHospitalCode() {
        return hospitalCode;
    }

    public void setHospitalCode(String strHospitalCode) {
        this.hospitalCode = strHospitalCode;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String strTaxId) {
        this.taxId = strTaxId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String strAddress) {
        this.address = strAddress;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String strCity) {
        this.city = strCity;
    }

    public String getStateRegion() {
        return stateRegion;
    }

    public void setStateRegion(String strStateRegion) {
        this.stateRegion = strStateRegion;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String strPostalCode) {
        this.postalCode = strPostalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String strCountry) {
        this.country = strCountry;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String strContactEmail) {
        this.contactEmail = strContactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String strContactPhone) {
        this.contactPhone = strContactPhone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<HospitalAlias> getAliases() {
        return aliases;
    }

    public void setAliases(List<HospitalAlias> alAliases) {
        this.aliases = alAliases;
    }
}
