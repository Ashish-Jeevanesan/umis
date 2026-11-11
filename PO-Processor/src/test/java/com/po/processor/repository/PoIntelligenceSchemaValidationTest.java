package com.po.processor.repository;

import com.po.processor.entity.ExtractedPoLineItem;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.Hospital;
import com.po.processor.entity.HospitalAlias;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.PoApprovalHistory;
import com.po.processor.entity.PoBillingDetail;
import com.po.processor.entity.PoBodyAttachmentComparison;
import com.po.processor.entity.PoDocumentAnalysis;
import com.po.processor.entity.PoDuplicateDetection;
import com.po.processor.entity.PoExtractionRun;
import com.po.processor.entity.PoFieldEditHistory;
import com.po.processor.entity.PoFieldEvidence;
import com.po.processor.entity.PoPatientDetail;
import com.po.processor.entity.PoShippingDetail;
import com.po.processor.entity.PoValidationResult;
import com.po.processor.entity.PoWorkflowStatus;
import com.po.processor.entity.ProcessingJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation test verifying the canonical schema.sql existence and entity repository persistence.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PoIntelligenceSchemaValidationTest {

    @Autowired
    private ProcessingJobRepository processingJobRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private HospitalAliasRepository hospitalAliasRepository;

    @Autowired
    private ExtractedPurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ExtractedPoLineItemRepository lineItemRepository;

    @Autowired
    private PoPatientDetailRepository patientDetailRepository;

    @Autowired
    private PoBillingDetailRepository billingDetailRepository;

    @Autowired
    private PoShippingDetailRepository shippingDetailRepository;

    @Autowired
    private PoDocumentAnalysisRepository documentAnalysisRepository;

    @Autowired
    private PoExtractionRunRepository extractionRunRepository;

    @Autowired
    private PoFieldEvidenceRepository fieldEvidenceRepository;

    @Autowired
    private PoBodyAttachmentComparisonRepository comparisonRepository;

    @Autowired
    private PoValidationResultRepository validationResultRepository;

    @Autowired
    private PoDuplicateDetectionRepository duplicateDetectionRepository;

    @Autowired
    private PoApprovalHistoryRepository approvalHistoryRepository;

    @Autowired
    private PoFieldEditHistoryRepository fieldEditHistoryRepository;

    @Test
    @DisplayName("Verify canonical schema.sql exists and contains all required table definitions")
    void testCanonicalSchemaSqlCompleteness() throws Exception {
        InputStream isStream = getClass().getResourceAsStream("/schema.sql");
        assertNotNull(isStream, "Canonical schema.sql must exist on classpath");

        String strSchemaDdl = new String(isStream.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(strSchemaDdl.contains("CREATE SCHEMA IF NOT EXISTS po_processor;"));
        assertTrue(strSchemaDdl.contains("po_processor.processing_jobs"));
        assertTrue(strSchemaDdl.contains("po_processor.hospitals"));
        assertTrue(strSchemaDdl.contains("po_processor.hospital_aliases"));
        assertTrue(strSchemaDdl.contains("po_processor.extracted_purchase_orders"));
        assertTrue(strSchemaDdl.contains("po_processor.extracted_po_line_items"));
        assertTrue(strSchemaDdl.contains("po_processor.po_patient_details"));
        assertTrue(strSchemaDdl.contains("po_processor.po_billing_details"));
        assertTrue(strSchemaDdl.contains("po_processor.po_shipping_details"));
        assertTrue(strSchemaDdl.contains("po_processor.po_document_analyses"));
        assertTrue(strSchemaDdl.contains("po_processor.po_extraction_runs"));
        assertTrue(strSchemaDdl.contains("po_processor.po_field_evidence"));
        assertTrue(strSchemaDdl.contains("po_processor.po_body_attachment_comparisons"));
        assertTrue(strSchemaDdl.contains("po_processor.po_validation_results"));
        assertTrue(strSchemaDdl.contains("po_processor.po_duplicate_detections"));
        assertTrue(strSchemaDdl.contains("po_processor.po_approval_history"));
        assertTrue(strSchemaDdl.contains("po_processor.po_field_edit_history"));
        assertTrue(strSchemaDdl.contains("version BIGINT NOT NULL DEFAULT 0"));
        assertTrue(strSchemaDdl.contains("is_superseded BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(strSchemaDdl.contains("uk_epo_tenant_po_revision"));
    }

    @Test
    @DisplayName("Verify end-to-end repository persistence and dynamic currency aggregation")
    void testEntityPersistenceAndCurrencyAggregation() {
        UUID tenantId = UUID.randomUUID();

        // 1. Processing Job with leasing
        ProcessingJob job = new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        job.setProcessingStatus(JobProcessingStatus.QUEUED);
        job.getWorkerLease().setLockedBy("worker-node-1");
        job.getWorkerLease().setLockExpiresAt(Instant.now().plusSeconds(300));
        job.getExtractedSummary().setPoNumber("PO-2026-9901");
        job.getExtractedSummary().setTotalAmount(new BigDecimal("12500.50"));
        job.getExtractedSummary().setCurrency("EUR");
        ProcessingJob savedJob = processingJobRepository.save(job);
        assertNotNull(savedJob.getId());

        // 2. Hospital and alias
        Hospital hospital = new Hospital(tenantId, "st-jude-hospital");
        hospital.setLegalName("St. Jude Children's Research Hospital");
        hospital.setCountry("USA");
        Hospital savedHospital = hospitalRepository.save(hospital);

        HospitalAlias alias = new HospitalAlias(savedHospital, "St. Jude Hospital", new BigDecimal("0.98"));
        hospitalAliasRepository.save(alias);

        // 3. Extracted Purchase Order
        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(savedJob.getId());
        po.setTenantId(tenantId);
        po.setHospital(savedHospital);
        po.setPoNumber("PO-2026-9901");
        po.setPoDate(LocalDate.now());
        po.setCurrency("EUR");
        po.setSubtotal(new BigDecimal("10000.00"));
        po.setGrandTotal(new BigDecimal("12500.50"));
        po.setWorkflowStatus(PoWorkflowStatus.APPROVED);
        po.setApproved(true);
        po.setApprovedBy("admin@hospital.org");
        po.setApprovedAt(Instant.now());
        ExtractedPurchaseOrder savedPo = purchaseOrderRepository.save(po);

        // 4. Line Items
        ExtractedPoLineItem item = new ExtractedPoLineItem();
        item.setPurchaseOrder(savedPo);
        item.setLineNumber(1);
        item.setProductCode("SPINE-IMPLANT-01");
        item.setQuantity(new BigDecimal("5.000"));
        item.setUnitPrice(new BigDecimal("2000.00"));
        item.setLineTotal(new BigDecimal("10000.00"));
        item.setCurrency("EUR");
        lineItemRepository.save(item);

        // 5. Patient, Billing, Shipping
        PoPatientDetail patient = new PoPatientDetail(savedPo);
        patient.setPatientName("Anonymous Subject");
        patientDetailRepository.save(patient);

        PoBillingDetail billing = new PoBillingDetail(savedPo);
        billing.setBillingEntity("St. Jude Accounts Payable");
        billingDetailRepository.save(billing);

        PoShippingDetail shipping = new PoShippingDetail(savedPo);
        shipping.setShipToEntity("St. Jude Logistics Dock A");
        shippingDetailRepository.save(shipping);

        // 6. Verification of dynamic currency aggregation
        List<ExtractedPurchaseOrderRepository.CurrencySummaryProjection> alAggregates =
                purchaseOrderRepository.findApprovedTotalsByCurrency(tenantId, savedHospital.getId());

        assertFalse(alAggregates.isEmpty());
        assertEquals("EUR", alAggregates.get(0).getCurrency());
        assertEquals(1, alAggregates.get(0).getApprovedCount());
        assertEquals(0, new BigDecimal("12500.50").compareTo(alAggregates.get(0).getTotalAmount()));
    }
}
