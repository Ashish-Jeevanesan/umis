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
 * Entity representing an alternative name or alias for a hospital.
 */
@Entity
@Table(name = "hospital_aliases", schema = "po_processor")
public class HospitalAlias {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id", nullable = false)
    private Hospital hospital;

    @Column(name = "alias_name", nullable = false, length = 255)
    private String aliasName;

    @Column(name = "confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence = new BigDecimal("1.00");

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public HospitalAlias() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public HospitalAlias(Hospital hospital, String strAliasName, BigDecimal confidence) {
        this();
        this.hospital = hospital;
        this.aliasName = strAliasName;
        this.confidence = confidence != null ? confidence : new BigDecimal("1.00");
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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Hospital getHospital() {
        return hospital;
    }

    public void setHospital(Hospital hospital) {
        this.hospital = hospital;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String strAliasName) {
        this.aliasName = strAliasName;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
