package com.po.processor.duplicate;

import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import com.po.processor.duplicate.dto.DuplicateCheckResult;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.PoDocumentAnalysis;
import com.po.processor.entity.PoDuplicateDetection;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.PoDocumentAnalysisRepository;
import com.po.processor.repository.PoDuplicateDetectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service evaluating incoming documents for exact duplicates, revisions, amendments, and reissues.
 */
@Service
public class DuplicateDetectionService {

    private static final Logger log = LoggerFactory.getLogger(DuplicateDetectionService.class);

    private final ExtractedPurchaseOrderRepository purchaseOrderRepository;
    private final PoDuplicateDetectionRepository duplicateDetectionRepository;
    private final PoDocumentAnalysisRepository docAnalysisRepository;
    private final PoConcurrencyLockService lockService;

    public DuplicateDetectionService(
            ExtractedPurchaseOrderRepository purchaseOrderRepository,
            PoDuplicateDetectionRepository duplicateDetectionRepository,
            PoDocumentAnalysisRepository docAnalysisRepository,
            PoConcurrencyLockService lockService
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.duplicateDetectionRepository = duplicateDetectionRepository;
        this.docAnalysisRepository = docAnalysisRepository;
        this.lockService = lockService;
    }

    /**
     * Evaluates whether an incoming PO extraction is an exact duplicate, revision, or new document.
     *
     * @param jobId Current processing job ID
     * @param tenantId Tenant identifier
     * @param poDto Extracted PO details
     * @return DuplicateCheckResult classifying relationship to existing records
     */
    @Transactional
    public DuplicateCheckResult evaluateDuplicate(UUID jobId, UUID tenantId, ExtractedPurchaseOrderDto poDto) {
        if (poDto == null || poDto.poNumber() == null || poDto.poNumber().isBlank()) {
            return new DuplicateCheckResult("NONE", "No PO number present", BigDecimal.ONE, null, false, false);
        }

        String strPoNumber = poDto.poNumber().trim();
        lockService.acquirePoLock(tenantId, strPoNumber);

        Optional<ExtractedPurchaseOrder> optExisting = purchaseOrderRepository
                .findFirstByTenantIdAndPoNumberOrderByRevisionNumberDesc(tenantId, strPoNumber);

        if (optExisting.isEmpty()) {
            return new DuplicateCheckResult("NONE", "No prior record with this PO number", BigDecimal.ONE, null, false, false);
        }

        ExtractedPurchaseOrder originalPo = optExisting.get();

        // 1. Check exact document hash match
        boolean isHashMatch = checkFileHashMatch(jobId, originalPo.getProcessingJobId());

        // 2. Check total amount match
        boolean isAmountMatch = poDto.grandTotal() != null &&
                originalPo.getGrandTotal().compareTo(poDto.grandTotal()) == 0;

        if (isHashMatch && isAmountMatch) {
            recordDuplicateAudit(jobId, originalPo, "EXACT_DUPLICATE",
                    "Matching SHA-256 document hash and identical grand total: " + originalPo.getGrandTotal(),
                    new BigDecimal("0.99"), "CONFIRMED_DUPLICATE");

            log.info("Job [{}] identified as EXACT_DUPLICATE of PO [{}]", jobId, originalPo.getPoNumber());
            return new DuplicateCheckResult("EXACT_DUPLICATE", "Exact document hash and amount match",
                    new BigDecimal("0.99"), originalPo, true, false);
        }

        if (!isAmountMatch || !isHashMatch) {
            String strReason = !isAmountMatch
                    ? "Existing PO total [" + originalPo.getGrandTotal() + "] differs from incoming total [" + poDto.grandTotal() + "]"
                    : "Different document hash with identical PO number [" + strPoNumber + "]";

            recordDuplicateAudit(jobId, originalPo, "REVISED_PO", strReason, new BigDecimal("0.90"), "LINKED_REVISION");
            log.info("Job [{}] identified as REVISED_PO for original PO [{}]", jobId, originalPo.getPoNumber());
            return new DuplicateCheckResult("REVISED_PO", strReason, new BigDecimal("0.90"), originalPo, false, true);
        }

        return new DuplicateCheckResult("NONE", "No duplication detected", BigDecimal.ONE, null, false, false);
    }

    private boolean checkFileHashMatch(UUID incomingJobId, UUID originalJobId) {
        List<PoDocumentAnalysis> alIncomingDocs = docAnalysisRepository.findByProcessingJobId(incomingJobId);
        List<PoDocumentAnalysis> alOriginalDocs = docAnalysisRepository.findByProcessingJobId(originalJobId);

        for (PoDocumentAnalysis inDoc : alIncomingDocs) {
            if (inDoc.getFileHash() == null || inDoc.getFileHash().isBlank()) continue;
            for (PoDocumentAnalysis origDoc : alOriginalDocs) {
                if (inDoc.getFileHash().equalsIgnoreCase(origDoc.getFileHash())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void recordDuplicateAudit(
            UUID jobId,
            ExtractedPurchaseOrder origPo,
            String strType,
            String strReason,
            BigDecimal score,
            String strReviewStatus
    ) {
        PoDuplicateDetection detection = new PoDuplicateDetection(jobId, origPo, strType, strReason, score, strReviewStatus);
        duplicateDetectionRepository.save(detection);
    }
}
