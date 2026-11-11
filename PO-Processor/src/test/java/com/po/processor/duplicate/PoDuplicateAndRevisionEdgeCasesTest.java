package com.po.processor.duplicate;

import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import com.po.processor.ai.dto.ExtractedVendorDto;
import com.po.processor.duplicate.dto.DuplicateCheckResult;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.Hospital;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge-case test suite for duplicate classification, revised purchase orders, and cancelled/reissued lifecycles.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PoDuplicateAndRevisionEdgeCasesTest {

    @Autowired
    private DuplicateDetectionService duplicateService;

    @Autowired
    private ExtractedPurchaseOrderRepository poRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private ProcessingJobRepository jobRepository;

    private final UUID tenantId = UUID.fromString("77777777-8888-9999-0000-111122223333");

    private ExtractedPurchaseOrderDto createDto(String poNumber, BigDecimal total) {
        return new ExtractedPurchaseOrderDto(
                poNumber, "Surgical Order", LocalDate.now(), null, null,
                "USD", total, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, total,
                "Net 30", "Wire", null, null, null, null,
                "Mercy Hospital", "MERCY-01",
                new ExtractedVendorDto("Globus Medical", null, null, null, null, null, null),
                null, null, null, List.of(), List.of()
        );
    }

    @Test
    @DisplayName("1. Unrecorded PO number returns NONE with no duplicate classification")
    void testNewPoReturnsNone() {
        DuplicateCheckResult result = duplicateService.evaluateDuplicate(
                UUID.randomUUID(), tenantId, createDto("PO-BRAND-NEW", new BigDecimal("1000.00"))
        );
        assertEquals("NONE", result.duplicateType());
        assertFalse(result.isDuplicate());
        assertFalse(result.isRevision());
    }

    @Test
    @DisplayName("2. Existing PO number with different total is classified as REVISED_PO")
    void testAmendedTotalClassifiedAsRevisedPo() {
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "test-rev-hosp"));
        ProcessingJob job = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        ExtractedPurchaseOrder origPo = new ExtractedPurchaseOrder();
        origPo.setProcessingJobId(job.getId());
        origPo.setTenantId(tenantId);
        origPo.setHospital(hospital);
        origPo.setPoNumber("PO-AMEND-01");
        origPo.setRevisionNumber(1);
        origPo.setCurrency("USD");
        origPo.setGrandTotal(new BigDecimal("5000.00"));
        origPo = poRepository.save(origPo);

        // Ingest same PO number with amended amount $6,200.00
        DuplicateCheckResult result = duplicateService.evaluateDuplicate(
                UUID.randomUUID(), tenantId, createDto("PO-AMEND-01", new BigDecimal("6200.00"))
        );

        assertEquals("REVISED_PO", result.duplicateType());
        assertTrue(result.isRevision());
        assertNotNull(result.originalPurchaseOrder());
        assertEquals(origPo.getId(), result.originalPurchaseOrder().getId());
    }

    @Test
    @DisplayName("3. Cancelled PO is excluded from active purchasing totals")
    void testCancelledPoExcludedFromTotals() {
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "test-cancel-hosp"));
        ProcessingJob job = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(job.getId());
        po.setTenantId(tenantId);
        po.setHospital(hospital);
        po.setPoNumber("PO-CANCEL-01");
        po.setCurrency("USD");
        po.setGrandTotal(new BigDecimal("8000.00"));
        po.setWorkflowStatus(PoWorkflowStatus.CANCELLED);
        po.setApproved(false);
        po.setSuperseded(true);
        poRepository.save(po);

        // Query active totals for hospital
        List<ExtractedPurchaseOrderRepository.CurrencySummaryProjection> totals =
                poRepository.findApprovedTotalsByCurrency(tenantId, hospital.getId());
        assertTrue(totals.isEmpty());
    }
}
