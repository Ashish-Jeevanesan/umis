package com.po.processor.ai.dto;

import java.time.LocalDate;

/**
 * Data transfer object representing sensitive extracted patient and procedure reference data.
 */
public record ExtractedPatientDto(
        String patientName,
        String patientIdMrn,
        String patientRefNumber,
        LocalDate dateOfBirth,
        String procedureCaseReference,
        String surgeonDoctorName,
        String departmentSpecialty
) {}
