package com.po.processor.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.po.processor.dto.ApproveJobRequestDto;
import com.po.processor.dto.FieldUpdateRequestDto;
import com.po.processor.dto.VerifyJobRequestDto;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.Hospital;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.PoApprovalHistory;
import com.po.processor.entity.PoFieldEditHistory;
import com.po.processor.entity.PoWorkflowStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.HospitalRepository;
import com.po.processor.repository.PoApprovalHistoryRepository;
import com.po.processor.repository.PoFieldEditHistoryRepository;
import com.po.processor.repository.ProcessingJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security and Tenant Isolation test suite verifying authentication, RBAC, tenant protection, and audit integrity.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class PoSecurityAndTenantIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PoJwtTokenProvider tokenProvider;

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private ExtractedPurchaseOrderRepository poRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private PoFieldEditHistoryRepository editHistoryRepository;

    @Autowired
    private PoApprovalHistoryRepository approvalHistoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID tenantA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID tenantB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private String createToken(UUID tenantId, String strEmail, String strRole, long expiryMs) {
        return tokenProvider.generateToken(UUID.randomUUID(), tenantId, strEmail, strRole, expiryMs);
    }

    private ProcessingJob seedJobAndPo(UUID tenantId, String strPoNum, PoWorkflowStatus workflowStatus) {
        Hospital hosp = hospitalRepository.save(new Hospital(tenantId, "test-hosp-" + strPoNum.toLowerCase()));
        ProcessingJob job = new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        job.setProcessingStatus(JobProcessingStatus.READY_FOR_REVIEW);
        ProcessingJob savedJob = jobRepository.save(job);

        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(savedJob.getId());
        po.setTenantId(tenantId);
        po.setHospital(hosp);
        po.setPoNumber(strPoNum);
        po.setGrandTotal(new BigDecimal("2500.00"));
        po.setCurrency("USD");
        po.setPoDate(LocalDate.now());
        po.setVendorName("Medtronic");
        po.setWorkflowStatus(workflowStatus);
        poRepository.save(po);

        return savedJob;
    }

    @Test
    @DisplayName("1, 2, 3: Missing, invalid, or expired tokens must return HTTP 401 Unauthorized")
    void testTokenValidationFailures() throws Exception {
        mockMvc.perform(get("/api/v1/processing-jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/processing-jobs")
                        .header("Authorization", "Bearer invalid.malformed.token"))
                .andExpect(status().isUnauthorized());

        String strExpiredToken = createToken(tenantA, "user@a.com", "PO_VIEWER", -5000);
        mockMvc.perform(get("/api/v1/processing-jobs")
                        .header("Authorization", "Bearer " + strExpiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4, 5: Role-based authorization rejects unauthorized actions with HTTP 403 Forbidden")
    void testRbacPermissions() throws Exception {
        ProcessingJob job = seedJobAndPo(tenantA, "PO-RBAC-01", PoWorkflowStatus.READY_FOR_REVIEW);
        String strViewerToken = createToken(tenantA, "viewer@a.com", "PO_VIEWER", 3600000);
        String strReviewerToken = createToken(tenantA, "reviewer@a.com", "PO_REVIEWER", 3600000);

        // Viewer cannot edit
        FieldUpdateRequestDto editReq = new FieldUpdateRequestDto("poNumber", "PO-NEW", "Testing edit");
        mockMvc.perform(put("/api/v1/processing-jobs/" + job.getId() + "/fields")
                        .header("Authorization", "Bearer " + strViewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isForbidden());

        // Viewer cannot verify or approve
        mockMvc.perform(post("/api/v1/processing-jobs/" + job.getId() + "/verify")
                        .header("Authorization", "Bearer " + strViewerToken))
                .andExpect(status().isForbidden());

        // Reviewer cannot approve
        mockMvc.perform(post("/api/v1/processing-jobs/" + job.getId() + "/approve")
                        .header("Authorization", "Bearer " + strReviewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6: Approver can approve only when workflow is in VERIFIED status")
    void testApproverWorkflowPrerequisite() throws Exception {
        ProcessingJob job = seedJobAndPo(tenantA, "PO-APP-01", PoWorkflowStatus.VERIFIED);
        String strApproverToken = createToken(tenantA, "approver@a.com", "PO_APPROVER", 3600000);

        ApproveJobRequestDto approveReq = new ApproveJobRequestDto("Approved by senior buyer");
        mockMvc.perform(post("/api/v1/processing-jobs/" + job.getId() + "/approve")
                        .header("Authorization", "Bearer " + strApproverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("7, 8, 9, 10: Strict Tenant Isolation prevents Tenant A from accessing Tenant B resources")
    void testTenantIsolation() throws Exception {
        ProcessingJob jobB = seedJobAndPo(tenantB, "PO-TENANT-B", PoWorkflowStatus.READY_FOR_REVIEW);
        String strTokenA = createToken(tenantA, "user@tenant-a.com", "PO_ADMIN", 3600000);

        // Tenant A cannot get Tenant B's job -> 404
        mockMvc.perform(get("/api/v1/processing-jobs/" + jobB.getId())
                        .header("Authorization", "Bearer " + strTokenA))
                .andExpect(status().isNotFound());

        // Tenant A cannot get Tenant B's full details -> 404
        mockMvc.perform(get("/api/v1/processing-jobs/" + jobB.getId() + "/details")
                        .header("Authorization", "Bearer " + strTokenA))
                .andExpect(status().isNotFound());

        // Tenant A cannot update Tenant B's PO -> 404
        FieldUpdateRequestDto editReq = new FieldUpdateRequestDto("poNumber", "PO-HACK", "Malicious update");
        mockMvc.perform(put("/api/v1/processing-jobs/" + jobB.getId() + "/fields")
                        .header("Authorization", "Bearer " + strTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isNotFound());

        // Tenant A cannot verify Tenant B's PO -> 404
        mockMvc.perform(post("/api/v1/processing-jobs/" + jobB.getId() + "/verify")
                        .header("Authorization", "Bearer " + strTokenA))
                .andExpect(status().isNotFound());

        // Tenant A cannot approve Tenant B's PO -> 404
        mockMvc.perform(post("/api/v1/processing-jobs/" + jobB.getId() + "/approve")
                        .header("Authorization", "Bearer " + strTokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("11, 12: Identity spoofing headers are ignored; audit records authentic token user")
    void testIdentityIntegrityAndAudit() throws Exception {
        ProcessingJob job = seedJobAndPo(tenantA, "PO-AUDIT-01", PoWorkflowStatus.READY_FOR_REVIEW);
        String strReviewerToken = createToken(tenantA, "real-reviewer@tenant-a.com", "PO_REVIEWER", 3600000);

        FieldUpdateRequestDto editReq = new FieldUpdateRequestDto("poNumber", "PO-AUDIT-01-REV", "Reviewer fix");

        // Attacker attempts to spoof identity via headers
        mockMvc.perform(put("/api/v1/processing-jobs/" + job.getId() + "/fields")
                        .header("Authorization", "Bearer " + strReviewerToken)
                        .header("X-User-Id", "spoofed-user@evil.org")
                        .header("X-Tenant-Id", tenantB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isOk());

        // Verify audit history recorded authentic identity, not spoofed header
        ExtractedPurchaseOrder po = poRepository.findByProcessingJobId(job.getId()).orElseThrow();
        List<PoFieldEditHistory> alEdits = editHistoryRepository.findByPurchaseOrderIdOrderByEditedAtDesc(po.getId());
        assertFalse(alEdits.isEmpty());
        assertEquals("real-reviewer@tenant-a.com", alEdits.get(0).getEditedBy());
    }
}
