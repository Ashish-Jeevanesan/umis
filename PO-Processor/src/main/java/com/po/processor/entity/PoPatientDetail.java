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
 * Entity representing sensitive patient and clinical reference details.
 * Isolated from general dashboard lists and logs for privacy protection.
 */
@Entity
@Table(name = "po_patient_details", schema = "po_processor")
public class PoPatientDetail {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private ExtractedPurchaseOrder purchaseOrder;

    @Column(name = "patient_name", length = 255)
    private String patientName;

    @Column(name = "patient_id_mrn", length = 100)
    private String patientIdMrn;

    @Column(name = "patient_ref_number", length = 100)
    private String patientRefNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "procedure_case_reference", length = 255)
    private String procedureCaseReference;

    @Column(name = "surgeon_doctor_name", length = 255)
    private String surgeonDoctorName;

    @Column(name = "department_specialty", length = 255)
    private String departmentSpecialty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PoPatientDetail() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public PoPatientDetail(ExtractedPurchaseOrder purchaseOrder) {
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
    public String getPatientName() { return patientName; }
    public void setPatientName(String strPatientName) { this.patientName = strPatientName; }
    public String getPatientIdMrn() { return patientIdMrn; }
    public void setPatientIdMrn(String strPatientIdMrn) { this.patientIdMrn = strPatientIdMrn; }
    public String getPatientRefNumber() { return patientRefNumber; }
    public void setPatientRefNumber(String strPatientRefNumber) { this.patientRefNumber = strPatientRefNumber; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getProcedureCaseReference() { return procedureCaseReference; }
    public void setProcedureCaseReference(String strProcedureCaseReference) { this.procedureCaseReference = strProcedureCaseReference; }
    public String getSurgeonDoctorName() { return surgeonDoctorName; }
    public void setSurgeonDoctorName(String strSurgeonDoctorName) { this.surgeonDoctorName = strSurgeonDoctorName; }
    public String getDepartmentSpecialty() { return departmentSpecialty; }
    public void setDepartmentSpecialty(String strDepartmentSpecialty) { this.departmentSpecialty = strDepartmentSpecialty; }
    public Instant getCreatedAt() { return createdAt; }
}
