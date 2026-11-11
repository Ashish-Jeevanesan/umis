package com.po.processor.validation;

import com.po.processor.entity.ExtractedPoLineItem;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.Hospital;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.repository.ExtractedPoLineItemRepository;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.HospitalRepository;
import com.po.processor.repository.ProcessingJobRepository;
import com.po.processor.validation.dto.ValidationSummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge-case test suite for financial equations, rounding tolerances, missing fields, and tax validation.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PoValidationEdgeCasesTest {

    @Autowired
    private PoValidationService validationService;

    @Autowired
    private ExtractedPurchaseOrderRepository poRepository;

    @Autowired
    private ExtractedPoLineItemRepository lineItemRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private ProcessingJobRepository jobRepository;

    private final UUID tenantId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private ProcessingJob createJob() {
        return jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
    }

    @Test
    @DisplayName("1. PO missing mandatory PO number and currency fails with validation ERRORs")
    void testMissingMandatoryFieldsFails() {
        ProcessingJob job = createJob();
        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(job.getId());
        po.setTenantId(tenantId);
        po.setPoNumber("INITIAL");
        po.setCurrency("USD");
        po.setGrandTotal(new BigDecimal("100.00"));
        po = poRepository.save(po);

        // Blank out mandatory fields
        po.setPoNumber("");
        po.setCurrency("");

        ValidationSummaryDto summary = validationService.validatePurchaseOrder(po);
        assertFalse(summary.isValid());
        assertTrue(summary.errorCount() >= 2);
    }

    @Test
    @DisplayName("2. Non-positive grand total fails with validation ERROR")
    void testNonPositiveGrandTotalFails() {
        ProcessingJob job = createJob();
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "test-valid-hosp-1"));
        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(job.getId());
        po.setTenantId(tenantId);
        po.setHospital(hospital);
        po.setPoNumber("PO-VAL-01");
        po.setVendorName("Acme Med");
        po.setCurrency("USD");
        po.setGrandTotal(BigDecimal.ZERO); // Zero total
        po = poRepository.save(po);

        ValidationSummaryDto summary = validationService.validatePurchaseOrder(po);
        assertFalse(summary.isValid());
        assertTrue(summary.errorCount() >= 1);
    }

    @Test
    @DisplayName("3. Line item sum matching subtotal within 0.02 rounding tolerance passes")
    void testLineItemSumWithinRoundingTolerancePasses() {
        ProcessingJob job = createJob();
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "test-valid-hosp-2"));
        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(job.getId());
        po.setTenantId(tenantId);
        po.setHospital(hospital);
        po.setPoNumber("PO-VAL-02");
        po.setVendorName("Acme Med");
        po.setCurrency("USD");
        po.setSubtotal(new BigDecimal("100.00"));
        po.setGrandTotal(new BigDecimal("100.01")); // within 0.02
        po = poRepository.save(po);

        ExtractedPoLineItem item1 = new ExtractedPoLineItem();
        item1.setPurchaseOrder(po);
        item1.setLineNumber(1);
        item1.setQuantity(new BigDecimal("1.000"));
        item1.setUnitPrice(new BigDecimal("50.00"));
        item1.setLineTotal(new BigDecimal("50.00"));
        lineItemRepository.save(item1);

        ExtractedPoLineItem item2 = new ExtractedPoLineItem();
        item2.setPurchaseOrder(po);
        item2.setLineNumber(2);
        item2.setQuantity(new BigDecimal("1.000"));
        item2.setUnitPrice(new BigDecimal("50.01"));
        item2.setLineTotal(new BigDecimal("50.01")); // Sum is 100.01, diff is 0.01 <= 0.02
        lineItemRepository.save(item2);

        ValidationSummaryDto summary = validationService.validatePurchaseOrder(po);
        assertEquals(0, summary.errorCount());
        assertTrue(summary.isValid());
    }

    @Test
    @DisplayName("4. Financial balance equation (subtotal - discount + freight + tax == total) verified")
    void testFinancialBalanceEquation() {
        ProcessingJob job = createJob();
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "test-valid-hosp-3"));
        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(job.getId());
        po.setTenantId(tenantId);
        po.setHospital(hospital);
        po.setPoNumber("PO-VAL-03");
        po.setVendorName("Acme Med");
        po.setCurrency("USD");
        po.setSubtotal(new BigDecimal("1000.00"));
        po.setDiscount(new BigDecimal("100.00"));
        po.setFreightCharges(new BigDecimal("50.00"));
        po.setTaxAmount(new BigDecimal("95.00"));
        po.setGrandTotal(new BigDecimal("1045.00")); // 1000 - 100 + 50 + 95 = 1045
        po = poRepository.save(po);

        ValidationSummaryDto summary = validationService.validatePurchaseOrder(po);
        assertTrue(summary.isValid());
        assertEquals(0, summary.errorCount());
    }

    @Test
    @DisplayName("5. Financial balance equation violation produces WARNING finding")
    void testFinancialBalanceEquationViolationProducesWarning() {
        ProcessingJob job = createJob();
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "test-valid-hosp-4"));
        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(job.getId());
        po.setTenantId(tenantId);
        po.setHospital(hospital);
        po.setPoNumber("PO-VAL-04");
        po.setVendorName("Acme Med");
        po.setCurrency("USD");
        po.setSubtotal(new BigDecimal("1000.00"));
        po.setDiscount(BigDecimal.ZERO);
        po.setFreightCharges(BigDecimal.ZERO);
        po.setTaxAmount(BigDecimal.ZERO);
        po.setGrandTotal(new BigDecimal("1500.00")); // 1000 != 1500
        po = poRepository.save(po);

        ValidationSummaryDto summary = validationService.validatePurchaseOrder(po);
        assertTrue(summary.warningCount() >= 1);
        assertTrue(summary.validationResults().stream().anyMatch(r -> "FIN_BALANCE_EQUATION".equals(r.getRuleCode()) && !r.isPassed()));
    }
}
