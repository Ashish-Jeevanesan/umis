package com.po.processor.concurrency;

import com.po.processor.dto.ApproveJobRequestDto;
import com.po.processor.dto.FieldUpdateRequestDto;
import com.po.processor.duplicate.DuplicateDetectionService;
import com.po.processor.duplicate.dto.DuplicateCheckResult;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.Hospital;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.PoApprovalHistory;
import com.po.processor.entity.PoWorkflowStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.HospitalRepository;
import com.po.processor.repository.PoApprovalHistoryRepository;
import com.po.processor.repository.ProcessingJobRepository;
import com.po.processor.security.PoUserPrincipal;
import com.po.processor.service.PoWorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test suite verifying revision supersession, financial total correctness, concurrency locks, and optimistic locking.
 */
@SpringBootTest
@ActiveProfiles("test")
public class PoFinancialRevisionAndConcurrencyTest {

    @Autowired
    private ExtractedPurchaseOrderRepository poRepository;

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private PoApprovalHistoryRepository approvalHistoryRepository;

    @Autowired
    private PoWorkflowService workflowService;

    @Autowired
    private DuplicateDetectionService duplicateService;

    private final UUID tenantId = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Test
    @DisplayName("1. Approved revision supersedes previous revision without inflating historical totals")
    @Transactional
    void testApprovedRevisionTotalsSupersession() {
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "cleveland-clinic-rev"));

        // Revision 1: Approved for €5,000.00
        ProcessingJob job1 = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        ExtractedPurchaseOrder rev1 = new ExtractedPurchaseOrder();
        rev1.setProcessingJobId(job1.getId());
        rev1.setTenantId(tenantId);
        rev1.setHospital(hospital);
        rev1.setPoNumber("PO-SUPER-01");
        rev1.setRevisionNumber(1);
        rev1.setCurrency("EUR");
        rev1.setGrandTotal(new BigDecimal("5000.00"));
        rev1.setWorkflowStatus(PoWorkflowStatus.VERIFIED);
        rev1 = poRepository.save(rev1);

        PoUserPrincipal approver = new PoUserPrincipal(UUID.randomUUID(), tenantId, "buyer@cleveland.org", List.of("PO_APPROVER"));
        workflowService.approveJob(job1.getId(), approver, "buyer", "Rev 1 approved");

        // Totals after Rev 1 approval: €5,000.00
        List<ExtractedPurchaseOrderRepository.CurrencySummaryProjection> totals1 =
                poRepository.findApprovedTotalsByCurrency(tenantId, hospital.getId());
        assertEquals(1, totals1.size());
        assertEquals(0, new BigDecimal("5000.00").compareTo(totals1.get(0).getTotalAmount()));

        // Revision 2: Approved for €7,500.00
        ProcessingJob job2 = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        ExtractedPurchaseOrder rev2 = new ExtractedPurchaseOrder();
        rev2.setProcessingJobId(job2.getId());
        rev2.setTenantId(tenantId);
        rev2.setHospital(hospital);
        rev2.setPoNumber("PO-SUPER-01");
        rev2.setRevisionNumber(2);
        rev2.setParentPoId(rev1.getId());
        rev2.setCurrency("EUR");
        rev2.setGrandTotal(new BigDecimal("7500.00"));
        rev2.setWorkflowStatus(PoWorkflowStatus.VERIFIED);
        rev2 = poRepository.save(rev2);

        workflowService.approveJob(job2.getId(), approver, "buyer", "Rev 2 approved");

        // Verify Rev 1 is transactionally superseded
        ExtractedPurchaseOrder updatedRev1 = poRepository.findById(rev1.getId()).orElseThrow();
        assertTrue(updatedRev1.isSuperseded());
        assertEquals(rev2.getId(), updatedRev1.getSupersededById());
        assertEquals(PoWorkflowStatus.SUPERSEDED, updatedRev1.getWorkflowStatus());

        // Verify totals after Rev 2 approval: MUST be €7,500.00 (NOT €12,500.00)
        List<ExtractedPurchaseOrderRepository.CurrencySummaryProjection> totals2 =
                poRepository.findApprovedTotalsByCurrency(tenantId, hospital.getId());
        assertEquals(1, totals2.size());
        assertEquals(1, totals2.get(0).getApprovedCount());
        assertEquals(0, new BigDecimal("7500.00").compareTo(totals2.get(0).getTotalAmount()));
    }

    @Test
    @DisplayName("2. Rejected revision does not supersede approved revision")
    @Transactional
    void testRejectedRevisionDoesNotSupersedeApproved() {
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "mayo-reject-test"));

        // Revision 1: Approved for $10,000.00
        ProcessingJob job1 = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        ExtractedPurchaseOrder rev1 = new ExtractedPurchaseOrder();
        rev1.setProcessingJobId(job1.getId());
        rev1.setTenantId(tenantId);
        rev1.setHospital(hospital);
        rev1.setPoNumber("PO-REJ-01");
        rev1.setRevisionNumber(1);
        rev1.setCurrency("USD");
        rev1.setGrandTotal(new BigDecimal("10000.00"));
        rev1.setWorkflowStatus(PoWorkflowStatus.VERIFIED);
        rev1 = poRepository.save(rev1);

        PoUserPrincipal approver = new PoUserPrincipal(UUID.randomUUID(), tenantId, "approver@mayo.org", List.of("PO_APPROVER"));
        workflowService.approveJob(job1.getId(), approver, "approver", "Rev 1 OK");

        // Revision 2: Submitted but rejected
        ProcessingJob job2 = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        ExtractedPurchaseOrder rev2 = new ExtractedPurchaseOrder();
        rev2.setProcessingJobId(job2.getId());
        rev2.setTenantId(tenantId);
        rev2.setHospital(hospital);
        rev2.setPoNumber("PO-REJ-01");
        rev2.setRevisionNumber(2);
        rev2.setParentPoId(rev1.getId());
        rev2.setCurrency("USD");
        rev2.setGrandTotal(new BigDecimal("18000.00"));
        rev2.setWorkflowStatus(PoWorkflowStatus.NEEDS_REVIEW);
        rev2 = poRepository.save(rev2);

        workflowService.rejectJob(job2.getId(), approver, "approver", "Price exceeds budget");

        // Verify Rev 2 rejected and Rev 1 remains active approved
        ExtractedPurchaseOrder updatedRev2 = poRepository.findById(rev2.getId()).orElseThrow();
        assertEquals(PoWorkflowStatus.REJECTED, updatedRev2.getWorkflowStatus());
        assertFalse(updatedRev2.isApproved());

        ExtractedPurchaseOrder updatedRev1 = poRepository.findById(rev1.getId()).orElseThrow();
        assertFalse(updatedRev1.isSuperseded());
        assertTrue(updatedRev1.isApproved());

        // Historical totals remain $10,000.00
        List<ExtractedPurchaseOrderRepository.CurrencySummaryProjection> totals =
                poRepository.findApprovedTotalsByCurrency(tenantId, hospital.getId());
        assertEquals(1, totals.size());
        assertEquals(0, new BigDecimal("10000.00").compareTo(totals.get(0).getTotalAmount()));
    }

    @Test
    @DisplayName("5, 6: JPA @Version detects concurrent edits and throws OptimisticLockException")
    void testOptimisticLockingOnConcurrentEdits() {
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "optimistic-hospital"));
        ProcessingJob job = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(job.getId());
        po.setTenantId(tenantId);
        po.setHospital(hospital);
        po.setPoNumber("PO-OPT-01");
        po.setCurrency("USD");
        po.setGrandTotal(new BigDecimal("1000.00"));
        po = poRepository.save(po);

        // Reviewer 1 loads entity
        ExtractedPurchaseOrder instance1 = poRepository.findById(po.getId()).orElseThrow();

        // Reviewer 2 loads same entity
        ExtractedPurchaseOrder instance2 = poRepository.findById(po.getId()).orElseThrow();

        // Reviewer 1 modifies and commits
        instance1.setGrandTotal(new BigDecimal("1200.00"));
        poRepository.saveAndFlush(instance1);

        // Reviewer 2 attempts to save stale entity -> MUST throw OptimisticLockingFailureException
        instance2.setGrandTotal(new BigDecimal("1500.00"));
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            poRepository.saveAndFlush(instance2);
        });
    }

    @Test
    @DisplayName("8, 9: Approval rejected if PO is edited concurrently or stale")
    @Transactional
    void testApprovalAgainstStaleVerification() {
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "stale-verify-hosp"));
        ProcessingJob job = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(job.getId());
        po.setTenantId(tenantId);
        po.setHospital(hospital);
        po.setPoNumber("PO-STALE-01");
        po.setCurrency("USD");
        po.setGrandTotal(new BigDecimal("2000.00"));
        po.setWorkflowStatus(PoWorkflowStatus.VERIFIED);
        poRepository.save(po);

        PoUserPrincipal reviewer = new PoUserPrincipal(UUID.randomUUID(), tenantId, "rev@hosp.org", List.of("PO_REVIEWER"));
        PoUserPrincipal approver = new PoUserPrincipal(UUID.randomUUID(), tenantId, "app@hosp.org", List.of("PO_APPROVER"));

        // Field edit happens -> invalidates verification to EDITED
        workflowService.updateField(job.getId(), new FieldUpdateRequestDto("grandTotal", "2500.00", "Price change"), reviewer, "rev");

        // Attempting approval MUST fail because status is EDITED, not VERIFIED
        assertThrows(IllegalStateException.class, () -> {
            workflowService.approveJob(job.getId(), approver, "app", "Approving stale record");
        });
    }
}
