package com.po.processor.integration;

import com.po.processor.dto.ProcessingJobFullDetailsDto;
import com.po.processor.dto.event.EmailIngestedPayloadDto;
import com.po.processor.dto.event.UmisEventDto;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.exception.IncompleteProcessingException;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.ProcessingJobRepository;
import com.po.processor.service.PoProcessingService;
import com.po.processor.service.PoWorkflowService;
import com.po.processor.storage.StorageReader;
import com.po.processor.worker.PoJobWorkerService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class PoAttachmentAndLineItemEndToEndIntegrationTest {

    @Autowired
    private PoProcessingService processingService;

    @Autowired
    private PoJobWorkerService workerService;

    @Autowired
    private PoWorkflowService workflowService;

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private ExtractedPurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private com.po.processor.repository.ExtractedPoLineItemRepository lineItemRepository;

    @Autowired
    private com.po.processor.validation.PoValidationService validationService;

    @Autowired
    private StorageReader storageReader;

    private UUID tenantId;
    private UUID mailboxId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        mailboxId = UUID.randomUUID();
    }

    private byte[] createSyntheticPoPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                stream.beginText();
                stream.setLeading(14.5f);
                stream.newLineAtOffset(50, 720);

                stream.showText("PURCHASE ORDER: PO-2026-MULTI-5");
                stream.newLine();
                stream.showText("Hospital: Cleveland Clinic Foundation");
                stream.newLine();
                stream.showText("Vendor: Globus Medical Inc.");
                stream.newLine();
                stream.showText("PO Date: 2026-11-09");
                stream.newLine();
                stream.showText("Delivery Date: 2026-11-20");
                stream.newLine();
                stream.showText("Currency: USD");
                stream.newLine();
                stream.newLine();

                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                stream.showText("Line 1 | SKU-SP-001 | Titanium Pedicle Screw 6.5x45mm | Qty: 10 | EA | Price: 450.00 | Total: 4500.00");
                stream.newLine();
                stream.showText("Line 2 | SKU-SP-002 | PEEK Spinal Fusion Cage 12x14mm | Qty: 4 | EA | Price: 1200.00 | Total: 4800.00");
                stream.newLine();
                stream.showText("Line 3 | SKU-SP-003 | Occipital Cervical Plate 4-Hole | Qty: 2 | EA | Price: 850.00 | Total: 1700.00");
                stream.newLine();
                stream.showText("Line 4 | SKU-SP-004 | Crosslink Connector Adjustable 50mm | Qty: 5 | EA | Price: 320.00 | Total: 1600.00");
                stream.newLine();
                stream.showText("Line 5 | SKU-SP-005 | Bone Graft Substitute Matrix 10cc | Qty: 3 | EA | Price: 650.00 | Total: 1950.00");
                stream.newLine();
                stream.newLine();

                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                stream.showText("Subtotal: 14550.00");
                stream.newLine();
                stream.showText("Tax: 347.00");
                stream.newLine();
                stream.showText("Grand Total: 14897.00 USD");
                stream.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("End-to-End: Ingest real PDF attachment with 5 line items, verify progress and review lock")
    @Transactional
    void testEndToEndPdfIngestionAndLineItemExtraction() throws Exception {
        UUID emailId = UUID.randomUUID();
        UUID attId = UUID.randomUUID();
        byte[] pdfBytes = createSyntheticPoPdf();

        // Save PDF to storage reader's path
        String storageRef = "attachments/" + tenantId + "/" + emailId + "/" + attId + "/content";
        Path attPath = Path.of("target/test-storage/" + storageRef).toAbsolutePath().normalize();
        Files.createDirectories(attPath.getParent());
        Files.write(attPath, pdfBytes);

        String strBody = "Please find attached our official purchase order PO-2026-MULTI-5.\nTotal Amount: 14897.00 USD";
        String bodyRef = "emails/" + tenantId + "/" + mailboxId + "/" + emailId + "/body.txt";
        Path bodyPath = Path.of("target/test-storage/" + bodyRef).toAbsolutePath().normalize();
        Files.createDirectories(bodyPath.getParent());
        Files.writeString(bodyPath, strBody);

        EmailIngestedPayloadDto.AttachmentReferenceDto attDto = new EmailIngestedPayloadDto.AttachmentReferenceDto(
                attId, "PO_Cleveland_Clinic.pdf", "application/pdf", pdfBytes.length, storageRef, "hash123"
        );

        EmailIngestedPayloadDto payload = new EmailIngestedPayloadDto(
                emailId, mailboxId, tenantId, "Purchase Order PO-2026-MULTI-5 - Cleveland Clinic",
                "orders@clevelandclinic.org", "Cleveland Clinic", List.of("sales@globusmedical.com"),
                "PO Attached", bodyRef, null, true, List.of(attDto)
        );

        UmisEventDto<EmailIngestedPayloadDto> event = new UmisEventDto<>(
                UUID.randomUUID(), "EMAIL_INGESTED", "1.0", Instant.now(), tenantId, mailboxId, payload
        );

        // 1. Process Event
        ProcessingJob job = processingService.processEmailIngestedEvent(event);
        assertNotNull(job);
        assertEquals(1, job.getAttachments().size());
        assertEquals(0, job.getProgress().getProgressPercentage());

        // 2. Verify Review Lock while in QUEUED/PROCESSING
        job.setProcessingStatus(JobProcessingStatus.PROCESSING);
        job.getProgress().update("AI_EXTRACTION", 65, "Extracting details");
        final ProcessingJob processingJob = jobRepository.save(job);

        assertThrows(IncompleteProcessingException.class, () -> {
            if (processingJob.getProcessingStatus() == JobProcessingStatus.PROCESSING) {
                throw new IncompleteProcessingException("Review locked", 65, "AI_EXTRACTION");
            }
        });

        // 3. Execute Worker Pipeline
        workerService.claimAndExecute(job.getId());

        // 4. Verify Final Job Progress and Status
        ProcessingJob completedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobProcessingStatus.READY_FOR_REVIEW, completedJob.getProcessingStatus());
        assertEquals(100, completedJob.getProgress().getProgressPercentage());
        assertEquals("COMPLETED", completedJob.getProgress().getCurrentProcessingStage());

        // 5. Verify Extracted Purchase Order & Line Items
        ProcessingJobFullDetailsDto details = workflowService.getJobFullDetails(job.getId());
        assertNotNull(details);
        assertNotNull(details.purchaseOrder());
        assertEquals("PO-2026-MULTI-5", details.purchaseOrder().getPoNumber());
        assertEquals(new BigDecimal("14897.00").setScale(2), details.purchaseOrder().getGrandTotal().setScale(2));
        assertEquals(new BigDecimal("14550.00").setScale(2), details.purchaseOrder().getSubtotal().setScale(2));
        assertEquals("USD", details.purchaseOrder().getCurrency());

        // 6. Verify Line Items Count & Exact Math (Qty x UnitPrice = LineTotal)
        assertNotNull(details.lineItems());
        assertEquals(5, details.lineItems().size());

        for (var item : details.lineItems()) {
            assertNotNull(item.getQuantity());
            assertNotNull(item.getUnitPrice());
            assertNotNull(item.getLineTotal());
            BigDecimal expectedLineTotal = item.getQuantity().multiply(item.getUnitPrice());
            assertEquals(expectedLineTotal.setScale(2), item.getLineTotal().setScale(2),
                    "Line " + item.getLineNumber() + " arithmetic must be Qty x UnitPrice = LineTotal");
        }

        BigDecimal sumLineTotals = details.lineItems().stream()
                .map(item -> item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("14550.00").setScale(2), sumLineTotals.setScale(2));
    }

    @Test
    @DisplayName("Financial Validation: Arithmetic discrepancy in line totals triggers WARNING")
    @Transactional
    void testLineItemArithmeticDiscrepancyGeneratesWarning() {
        ProcessingJob job = new ProcessingJob(tenantId, UUID.randomUUID(), mailboxId, UUID.randomUUID(), Instant.now());
        job.setEmailSubject("PO-DISCREPANCY-TEST");
        job = jobRepository.save(job);

        ExtractedPurchaseOrder po = new ExtractedPurchaseOrder();
        po.setProcessingJobId(job.getId());
        po.setTenantId(tenantId);
        po.setPoNumber("PO-DISCREP-01");
        po.setCurrency("USD");
        po.setSubtotal(new BigDecimal("6000.00"));
        po.setGrandTotal(new BigDecimal("6000.00"));
        po = purchaseOrderRepository.save(po);

        com.po.processor.entity.ExtractedPoLineItem item = new com.po.processor.entity.ExtractedPoLineItem();
        item.setPurchaseOrder(po);
        item.setLineNumber(1);
        item.setProductCode("SKU-SP-002");
        item.setQuantity(new BigDecimal("4"));
        item.setUnitPrice(new BigDecimal("1200.00"));
        item.setLineTotal(new BigDecimal("4700.00")); // Inconsistent! Expected 4800.00
        lineItemRepository.save(item);

        var summary = validationService.validatePurchaseOrder(po);
        assertNotNull(summary);
        assertTrue(summary.warningCount() > 0, "Arithmetic discrepancy must trigger a warning");

        boolean arithmeticWarningFound = summary.validationResults().stream()
                .anyMatch(r -> "FIN_LINE_ARITHMETIC".equals(r.getRuleCode()) && !r.isPassed());
        assertTrue(arithmeticWarningFound, "FIN_LINE_ARITHMETIC discrepancy rule must fail");
    }
}
