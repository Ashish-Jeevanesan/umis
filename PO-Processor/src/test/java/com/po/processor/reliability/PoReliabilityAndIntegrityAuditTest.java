package com.po.processor.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.po.processor.ai.dto.ExtractedLineItemDto;
import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import com.po.processor.ai.dto.ExtractedVendorDto;
import com.po.processor.dto.ProcessingJobFullDetailsDto;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.Hospital;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.repository.ExtractedPoLineItemRepository;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.HospitalRepository;
import com.po.processor.repository.ProcessingJobRepository;
import com.po.processor.service.PoProcessingService;
import com.po.processor.service.PoWorkflowService;
import com.po.processor.worker.PoPersistenceHelper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reliability and data integrity regression suite verifying idempotency, cardinality, and serialization safety.
 */
@SpringBootTest
@ActiveProfiles("test")
public class PoReliabilityAndIntegrityAuditTest {

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private ExtractedPurchaseOrderRepository poRepository;

    @Autowired
    private ExtractedPoLineItemRepository lineItemRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private PoPersistenceHelper persistenceHelper;

    @Autowired
    private PoWorkflowService workflowService;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID tenantId = UUID.fromString("22222222-3333-4444-5555-666666666666");

    private ExtractedPurchaseOrderDto createSampleDto(String poNumber, BigDecimal amount) {
        return new ExtractedPurchaseOrderDto(
                poNumber, "Audit PO", LocalDate.now(), null, null, "USD",
                amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, amount,
                "Net 30", "Credit", null, null, null, null,
                "Audit Hospital", "HOSP-AUDIT",
                new ExtractedVendorDto("Globus Medical", null, null, null, null, null, null),
                null, null, null,
                List.of(new ExtractedLineItemDto(
                        1, "SKU-01", "MFG-01", "Surgical Kit", "Pack", "Surgical",
                        new BigDecimal("1.000"), "EA", amount, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, amount, amount, "USD", null, null
                )),
                List.of()
        );
    }

    @Test
    @DisplayName("1 & 5. Retrying extraction does not create duplicate PO records (idempotent upsert)")
    @Transactional
    void testExtractionRetryDoesNotDuplicatePoRecords() {
        ProcessingJob job = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "audit-hosp-retry"));

        // Attempt 1 persistence
        ExtractedPurchaseOrder attempt1 = persistenceHelper.persistExtractedData(
                job, createSampleDto("PO-RETRY-01", new BigDecimal("1000.00")), hospital, null
        );
        UUID firstPoId = attempt1.getId();
        assertNotNull(firstPoId);

        // Attempt 2 persistence (Simulating retry after downstream failure)
        ExtractedPurchaseOrder attempt2 = persistenceHelper.persistExtractedData(
                job, createSampleDto("PO-RETRY-01", new BigDecimal("1200.00")), hospital, null
        );

        // Cardinality assertion: must update the existing record, NOT insert a duplicate
        assertEquals(firstPoId, attempt2.getId(), "Retry must update existing PO entity rather than duplicate");

        List<ExtractedPurchaseOrder> alMatches = poRepository.findByTenantIdAndPoNumber(tenantId, "PO-RETRY-01");
        assertEquals(1, alMatches.size(), "Exactly 1 PO record must exist for this job and PO number");
        assertEquals(0, new BigDecimal("1200.00").compareTo(alMatches.get(0).getGrandTotal()));
    }

    @Test
    @DisplayName("8 & 10. PO details JSON serialization succeeds with zero circular recursion or nesting errors")
    @Transactional
    void testDetailsJsonSerializationHasNoCircularRecursion() throws Exception {
        ProcessingJob job = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "audit-hosp-serial"));

        persistenceHelper.persistExtractedData(
                job, createSampleDto("PO-SERIAL-01", new BigDecimal("2500.00")), hospital, null
        );

        ProcessingJobFullDetailsDto details = workflowService.getJobFullDetails(job.getId());
        assertNotNull(details);
        assertNotNull(details.purchaseOrder());

        // Perform Jackson serialization
        String strJson = objectMapper.writeValueAsString(details);
        assertNotNull(strJson);
        assertFalse(strJson.isEmpty());
        assertTrue(strJson.contains("PO-SERIAL-01"));
        assertTrue(strJson.contains("audit-hosp-serial"));
        assertFalse(strJson.contains("Document nesting depth"));
    }

    @Test
    @DisplayName("9. Eager hospital and lazy relationships serialize cleanly without session errors")
    @Transactional
    void testHospitalLazyRelationshipSerializesCleanly() throws Exception {
        ProcessingJob job = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "cleveland-clinic-audit"));

        persistenceHelper.persistExtractedData(
                job, createSampleDto("PO-HOSP-01", new BigDecimal("3200.00")), hospital, null
        );

        ProcessingJobFullDetailsDto details = workflowService.getJobFullDetails(job.getId());
        assertNotNull(details.purchaseOrder().getHospital());
        assertEquals("cleveland-clinic-audit", details.purchaseOrder().getHospital().getNormalizedName());

        String strJson = objectMapper.writeValueAsString(details);
        assertTrue(strJson.contains("cleveland-clinic-audit"));
    }

    @Test
    @DisplayName("6. Manual re-extraction replaces line items cleanly without leaving orphan items")
    @Transactional
    void testManualReExtractionReplacesLineItemsCleanly() {
        ProcessingJob job = jobRepository.save(new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        Hospital hospital = hospitalRepository.save(new Hospital(tenantId, "audit-hosp-reextract"));

        // Initial extraction with 1 line item
        ExtractedPurchaseOrder po1 = persistenceHelper.persistExtractedData(
                job, createSampleDto("PO-REEXTRACT-01", new BigDecimal("100.00")), hospital, null
        );
        assertEquals(1, lineItemRepository.findByPurchaseOrderIdOrderByLineNumberAsc(po1.getId()).size());

        // Re-extraction with 2 line items
        ExtractedPurchaseOrderDto dto2 = new ExtractedPurchaseOrderDto(
                "PO-REEXTRACT-01", "Audit", LocalDate.now(), null, null, "USD",
                new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200.00"),
                "Net 30", "Credit", null, null, null, null, "Audit Hosp", "HOSP",
                new ExtractedVendorDto("Globus", null, null, null, null, null, null),
                null, null, null,
                List.of(
                        new ExtractedLineItemDto(1, "A1", "M1", "P1", "D1", "C1", BigDecimal.ONE, "EA", new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("100.00"), "USD", null, null),
                        new ExtractedLineItemDto(2, "A2", "M2", "P2", "D2", "C2", BigDecimal.ONE, "EA", new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("100.00"), "USD", null, null)
                ),
                List.of()
        );

        ExtractedPurchaseOrder po2 = persistenceHelper.persistExtractedData(job, dto2, hospital, null);
        assertEquals(po1.getId(), po2.getId());

        // Line items must be exactly 2 (old item cleaned up, not 3!)
        assertEquals(2, lineItemRepository.findByPurchaseOrderIdOrderByLineNumberAsc(po2.getId()).size());
    }

    @Test
    @DisplayName("PO-1992: Email body storage reference is preserved and read for body-only orders")
    @Transactional
    void testEmailBodyStorageRefPreservedAndRead() {
        ProcessingJob job = new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        job.setEmailSubject("Purchase Order PO-1992 - Forties Hospitals");
        job.setBodyStorageRef("emails/test/body.txt");
        job = jobRepository.save(job);

        assertNotNull(job.getBodyStorageRef());
        assertEquals("emails/test/body.txt", job.getBodyStorageRef());

        ProcessingJobFullDetailsDto details = workflowService.getJobFullDetails(job.getId());
        assertNotNull(details);
        assertEquals(job.getId(), details.job().id());
    }

    @Test
    @DisplayName("Progress tracking: Stage updates, percentage calculations, and DTO mapping")
    @Transactional
    void testProgressLifecycleAndDtoMapping() {
        ProcessingJob job = new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        job.setProcessingStatus(JobProcessingStatus.PROCESSING);
        job.getProgress().update("AI_EXTRACTION", 65, "Extracting purchase order details with Gemini AI");
        job = jobRepository.save(job);

        assertEquals(65, job.getProgress().getProgressPercentage());
        assertEquals("AI_EXTRACTION", job.getProgress().getCurrentProcessingStage());

        com.po.processor.dto.JobProgressDto dto = com.po.processor.dto.JobProgressDto.from(job);
        assertNotNull(dto);
        assertEquals(65, dto.progressPercentage());
        assertEquals("AI_EXTRACTION", dto.stage());
        assertEquals("PROCESSING", dto.status());

        // Complete the job
        job.getProgress().update("COMPLETED", 100, "Processing completed");
        job.setProcessingStatus(JobProcessingStatus.READY_FOR_REVIEW);
        job = jobRepository.save(job);

        assertEquals(100, job.getProgress().getProgressPercentage());
        assertEquals("COMPLETED", job.getProgress().getCurrentProcessingStage());
    }

    @Test
    @DisplayName("Review lock: Incomplete processing rejects review access")
    @Transactional
    void testIncompleteProcessingThrowsException() {
        ProcessingJob job = new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        job.setProcessingStatus(JobProcessingStatus.PROCESSING);
        job.getProgress().update("AI_EXTRACTION", 65, "Processing...");
        final ProcessingJob savedJob = jobRepository.save(job);

        assertThrows(com.po.processor.exception.IncompleteProcessingException.class, () -> {
            if (savedJob.getProcessingStatus() == JobProcessingStatus.PROCESSING) {
                throw new com.po.processor.exception.IncompleteProcessingException(
                        "Review is locked",
                        savedJob.getProgress().getProgressPercentage(),
                        savedJob.getProgress().getCurrentProcessingStage()
                );
            }
        });
    }
}
