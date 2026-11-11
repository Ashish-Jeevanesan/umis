package com.po.processor.service;

import com.po.processor.dto.FieldUpdateRequestDto;
import com.po.processor.dto.ProcessingJobFullDetailsDto;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.Hospital;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.PoWorkflowStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.HospitalRepository;
import com.po.processor.repository.ProcessingJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests verifying the human review lifecycle: field editing, verification, approval, and audit history.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PoWorkflowIntegrationTest {

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private ExtractedPurchaseOrderRepository poRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private PoWorkflowService workflowService;

    @Test
    @DisplayName("Verify edit, verify, and approve lifecycle with audit trail and preconditions")
    void testWorkflowLifecycle() {
        UUID tenantId = UUID.randomUUID();
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "boston-childrens"));

        ProcessingJob job = new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        job.setProcessingStatus(JobProcessingStatus.READY_FOR_REVIEW);
        ProcessingJob savedJob = jobRepository.save(job);

        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(savedJob.getId());
        po.setTenantId(tenantId);
        po.setHospital(hospital);
        po.setPoNumber("PO-WORKFLOW-01");
        po.setCurrency("USD");
        po.setSubtotal(new BigDecimal("1000.00"));
        po.setGrandTotal(new BigDecimal("1000.00"));
        po.setVendorName("Medtronic Spine");
        po.setPoDate(LocalDate.now());
        po.setWorkflowStatus(PoWorkflowStatus.READY_FOR_REVIEW);
        ExtractedPurchaseOrder savedPo = poRepository.save(po);

        // 1. Cannot approve without verification
        assertThrows(IllegalStateException.class, () ->
                workflowService.approveJob(savedJob.getId(), "approver@hospital.org", "Trying early approval"));

        // 2. Perform field edit
        FieldUpdateRequestDto editReq = new FieldUpdateRequestDto("poNumber", "PO-WORKFLOW-01-REV", "Typo corrected");
        workflowService.updateField(savedJob.getId(), editReq, "editor@hospital.org");

        ExtractedPurchaseOrder editedPo = poRepository.findById(savedPo.getId()).orElseThrow();
        assertEquals("PO-WORKFLOW-01-REV", editedPo.getPoNumber());
        assertEquals(PoWorkflowStatus.EDITED, editedPo.getWorkflowStatus());
        assertNull(editedPo.getVerifiedBy());

        // 3. Verify Job
        workflowService.verifyJob(savedJob.getId(), "verifier@hospital.org", "Checked details");
        ExtractedPurchaseOrder verifiedPo = poRepository.findById(savedPo.getId()).orElseThrow();
        assertEquals(PoWorkflowStatus.VERIFIED, verifiedPo.getWorkflowStatus());
        assertEquals("verifier@hospital.org", verifiedPo.getVerifiedBy());
        assertNotNull(verifiedPo.getVerifiedAt());

        // 4. Approve Job
        workflowService.approveJob(savedJob.getId(), "approver@hospital.org", "Final sign-off");
        ExtractedPurchaseOrder approvedPo = poRepository.findById(savedPo.getId()).orElseThrow();
        assertEquals(PoWorkflowStatus.APPROVED, approvedPo.getWorkflowStatus());
        assertTrue(approvedPo.isApproved());
        assertEquals("approver@hospital.org", approvedPo.getApprovedBy());

        ProcessingJob completedJob = jobRepository.findById(savedJob.getId()).orElseThrow();
        assertEquals(JobProcessingStatus.COMPLETED, completedJob.getProcessingStatus());

        // 5. Attempting to edit approved PO must be rejected
        FieldUpdateRequestDto postApproveEdit = new FieldUpdateRequestDto("grandTotal", "9999.00", "Post approve change");
        assertThrows(IllegalStateException.class, () ->
                workflowService.updateField(savedJob.getId(), postApproveEdit, "hacker@test.com"));

        // 6. Check full details DTO
        ProcessingJobFullDetailsDto fullDetails = workflowService.getJobFullDetails(savedJob.getId());
        assertNotNull(fullDetails);
        assertEquals("PO-WORKFLOW-01-REV", fullDetails.purchaseOrder().getPoNumber());
        assertFalse(fullDetails.editHistory().isEmpty());
        assertFalse(fullDetails.approvalHistory().isEmpty());
    }
}
