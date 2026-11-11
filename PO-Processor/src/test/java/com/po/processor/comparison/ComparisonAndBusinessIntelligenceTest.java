package com.po.processor.comparison;

import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import com.po.processor.ai.dto.ExtractedVendorDto;
import com.po.processor.comparison.dto.ComparisonResultDto;
import com.po.processor.duplicate.DuplicateDetectionService;
import com.po.processor.duplicate.dto.DuplicateCheckResult;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.Hospital;
import com.po.processor.entity.PoWorkflowStatus;
import com.po.processor.hospital.HospitalMasterService;
import com.po.processor.hospital.dto.HospitalHistoryDto;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.HospitalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests verifying comparison engine, hospital normalization & history, and duplicate detection.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ComparisonAndBusinessIntelligenceTest {

    @Autowired
    private BodyAttachmentComparator comparator;

    @Autowired
    private HospitalMasterService hospitalMasterService;

    @Autowired
    private DuplicateDetectionService duplicateService;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private ExtractedPurchaseOrderRepository purchaseOrderRepository;

    @Test
    @DisplayName("Verify comparison engine detects matching values and mismatched totals")
    void testBodyAttachmentComparison() {
        ExtractedPurchaseOrderDto poDto = new ExtractedPurchaseOrderDto(
                "PO-2026-M01", "Orthopedic Supplies", LocalDate.now(), null, null,
                "USD", new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("500.00"), new BigDecimal("10.00"), new BigDecimal("5500.00"),
                "Net 30", null, null, null, null, null,
                "Mercy General Hospital", null,
                new ExtractedVendorDto("Globus Medical", null, null, null, null, null, null),
                null, null, null, List.of(), List.of()
        );

        // 1. Matching email body
        String strMatchingEmail = "Hello team, please find attached Purchase Order PO-2026-M01 for total $5,500.00 USD for Mercy General Hospital.";
        ComparisonResultDto matchRes = comparator.compare(strMatchingEmail, poDto, true);
        assertEquals("BODY_AND_ATTACHMENT_MATCH", matchRes.comparisonStatus());
        assertEquals(0, matchRes.conflictCount());

        // 2. Mismatched email body
        String strMismatchedEmail = "Please process attached PO-2026-M01 for total $9,999.00 USD.";
        ComparisonResultDto mismatchRes = comparator.compare(strMismatchedEmail, poDto, true);
        assertEquals("BODY_ATTACHMENT_MISMATCH", mismatchRes.comparisonStatus());
        assertTrue(mismatchRes.conflictCount() > 0);

        // 3. Short introductory email
        String strShortEmail = "Please find attached document.";
        ComparisonResultDto introRes = comparator.compare(strShortEmail, poDto, true);
        assertEquals("ATTACHMENT_ONLY_RELEVANT", introRes.comparisonStatus());
    }

    @Test
    @DisplayName("Verify hospital normalization and alias resolution")
    void testHospitalNormalizationAndAliases() {
        assertEquals("mercy", hospitalMasterService.normalizeHospitalName("Mercy General Hospital"));
        assertEquals("st-jude", hospitalMasterService.normalizeHospitalName("St. Jude Children's Research Hospital"));
        assertEquals("cedars-sinai", hospitalMasterService.normalizeHospitalName("Cedars-Sinai Medical Center"));

        UUID tenantId = UUID.randomUUID();
        Hospital h1 = hospitalMasterService.resolveOrCreateHospital(tenantId, "Mercy General Hospital", "MERCY-01");
        assertNotNull(h1.getId());
        assertEquals("mercy", h1.getNormalizedName());

        // Resolving variant with alias match
        Hospital h2 = hospitalMasterService.resolveOrCreateHospital(tenantId, "Mercy General Hospital", null);
        assertEquals(h1.getId(), h2.getId());
    }

    @Test
    @DisplayName("Verify dynamic multi-currency hospital lifetime purchasing totals")
    void testHospitalMultiCurrencyHistory() {
        UUID tenantId = UUID.randomUUID();
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "hopital-saint-louis"));

        // PO 1: EUR 50,000 (Approved)
        ExtractedPurchaseOrder po1 = new ExtractedPurchaseOrder();
        po1.setProcessingJobId(UUID.randomUUID());
        po1.setTenantId(tenantId);
        po1.setHospital(hospital);
        po1.setPoNumber("PO-EUR-1");
        po1.setCurrency("EUR");
        po1.setGrandTotal(new BigDecimal("50000.00"));
        po1.setApproved(true);
        po1.setWorkflowStatus(PoWorkflowStatus.APPROVED);
        purchaseOrderRepository.save(po1);

        // PO 2: EUR 25,000 (Approved)
        ExtractedPurchaseOrder po2 = new ExtractedPurchaseOrder();
        po2.setProcessingJobId(UUID.randomUUID());
        po2.setTenantId(tenantId);
        po2.setHospital(hospital);
        po2.setPoNumber("PO-EUR-2");
        po2.setCurrency("EUR");
        po2.setGrandTotal(new BigDecimal("25000.00"));
        po2.setApproved(true);
        po2.setWorkflowStatus(PoWorkflowStatus.APPROVED);
        purchaseOrderRepository.save(po2);

        // PO 3: USD 15,000 (Approved)
        ExtractedPurchaseOrder po3 = new ExtractedPurchaseOrder();
        po3.setProcessingJobId(UUID.randomUUID());
        po3.setTenantId(tenantId);
        po3.setHospital(hospital);
        po3.setPoNumber("PO-USD-1");
        po3.setCurrency("USD");
        po3.setGrandTotal(new BigDecimal("15000.00"));
        po3.setApproved(true);
        po3.setWorkflowStatus(PoWorkflowStatus.APPROVED);
        purchaseOrderRepository.save(po3);

        // PO 4: EUR 100,000 (DRAFT / Unapproved - must not count!)
        ExtractedPurchaseOrder po4 = new ExtractedPurchaseOrder();
        po4.setProcessingJobId(UUID.randomUUID());
        po4.setTenantId(tenantId);
        po4.setHospital(hospital);
        po4.setPoNumber("PO-UNAPPROVED");
        po4.setCurrency("EUR");
        po4.setGrandTotal(new BigDecimal("100000.00"));
        po4.setApproved(false);
        po4.setWorkflowStatus(PoWorkflowStatus.READY_FOR_REVIEW);
        purchaseOrderRepository.save(po4);

        HospitalHistoryDto history = hospitalMasterService.getHospitalHistory(tenantId, hospital.getId());
        assertEquals(3, history.totalApprovedPoCount());
        assertEquals(0, new BigDecimal("75000.00").compareTo(history.approvedTotalsByCurrency().get("EUR")));
        assertEquals(0, new BigDecimal("15000.00").compareTo(history.approvedTotalsByCurrency().get("USD")));
    }

    @Test
    @DisplayName("Verify duplicate and revision detection")
    void testDuplicateAndRevisionDetection() {
        UUID tenantId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        // Existing approved PO
        ExtractedPurchaseOrder existingPo = new ExtractedPurchaseOrder();
        existingPo.setProcessingJobId(jobId);
        existingPo.setTenantId(tenantId);
        existingPo.setPoNumber("PO-DUP-88");
        existingPo.setCurrency("USD");
        existingPo.setGrandTotal(new BigDecimal("1000.00"));
        existingPo.setApproved(true);
        existingPo.setWorkflowStatus(PoWorkflowStatus.APPROVED);
        purchaseOrderRepository.save(existingPo);

        // Incoming PO with same number but revised amount -> REVISED_PO
        ExtractedPurchaseOrderDto revisedDto = new ExtractedPurchaseOrderDto(
                "PO-DUP-88", "Revised Order", LocalDate.now(), null, null,
                "USD", new BigDecimal("1500.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1500.00"),
                null, null, null, null, null, null, null, null, null, null, null, null, List.of(), List.of()
        );

        DuplicateCheckResult dupCheck = duplicateService.evaluateDuplicate(UUID.randomUUID(), tenantId, revisedDto);
        assertTrue(dupCheck.isRevision());
        assertFalse(dupCheck.isDuplicate());
        assertEquals("REVISED_PO", dupCheck.duplicateType());
        assertEquals(existingPo.getId(), dupCheck.originalPurchaseOrder().getId());
    }
}
