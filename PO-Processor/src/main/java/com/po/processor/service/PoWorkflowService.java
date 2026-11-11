package com.po.processor.service;

import com.po.processor.dto.FieldUpdateRequestDto;
import com.po.processor.dto.ProcessingJobFullDetailsDto;
import com.po.processor.dto.ProcessingJobResponseDto;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.PoApprovalHistory;
import com.po.processor.entity.PoFieldEditHistory;
import com.po.processor.entity.PoWorkflowStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.exception.ResourceNotFoundException;
import com.po.processor.repository.ExtractedPoLineItemRepository;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.PoApprovalHistoryRepository;
import com.po.processor.repository.PoBillingDetailRepository;
import com.po.processor.repository.PoBodyAttachmentComparisonRepository;
import com.po.processor.repository.PoDocumentAnalysisRepository;
import com.po.processor.repository.PoDuplicateDetectionRepository;
import com.po.processor.repository.PoFieldEditHistoryRepository;
import com.po.processor.repository.PoPatientDetailRepository;
import com.po.processor.repository.PoShippingDetailRepository;
import com.po.processor.repository.PoValidationResultRepository;
import com.po.processor.repository.ProcessingJobRepository;
import com.po.processor.security.PoUserPrincipal;
import com.po.processor.validation.PoValidationService;
import com.po.processor.validation.dto.ValidationSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service orchestrating human review, audited field editing, verification, and final PO approval.
 */
@Service
public class PoWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(PoWorkflowService.class);

    private final ProcessingJobRepository jobRepository;
    private final ExtractedPurchaseOrderRepository poRepository;
    private final ExtractedPoLineItemRepository lineItemRepository;
    private final PoPatientDetailRepository patientRepository;
    private final PoBillingDetailRepository billingRepository;
    private final PoShippingDetailRepository shippingRepository;
    private final PoDocumentAnalysisRepository docAnalysisRepository;
    private final PoBodyAttachmentComparisonRepository comparisonRepository;
    private final PoValidationResultRepository validationRepository;
    private final PoDuplicateDetectionRepository duplicateRepository;
    private final PoFieldEditHistoryRepository editHistoryRepository;
    private final PoApprovalHistoryRepository approvalHistoryRepository;
    private final PoValidationService validationService;
    private final boolean allowSelfApproval;

    public PoWorkflowService(
            ProcessingJobRepository jobRepository,
            ExtractedPurchaseOrderRepository poRepository,
            ExtractedPoLineItemRepository lineItemRepository,
            PoPatientDetailRepository patientRepository,
            PoBillingDetailRepository billingRepository,
            PoShippingDetailRepository shippingRepository,
            PoDocumentAnalysisRepository docAnalysisRepository,
            PoBodyAttachmentComparisonRepository comparisonRepository,
            PoValidationResultRepository validationRepository,
            PoDuplicateDetectionRepository duplicateRepository,
            PoFieldEditHistoryRepository editHistoryRepository,
            PoApprovalHistoryRepository approvalHistoryRepository,
            PoValidationService validationService,
            @Value("${po.workflow.allow-self-approval:false}") boolean allowSelfApproval
    ) {
        this.jobRepository = jobRepository;
        this.poRepository = poRepository;
        this.lineItemRepository = lineItemRepository;
        this.patientRepository = patientRepository;
        this.billingRepository = billingRepository;
        this.shippingRepository = shippingRepository;
        this.docAnalysisRepository = docAnalysisRepository;
        this.comparisonRepository = comparisonRepository;
        this.validationRepository = validationRepository;
        this.duplicateRepository = duplicateRepository;
        this.editHistoryRepository = editHistoryRepository;
        this.approvalHistoryRepository = approvalHistoryRepository;
        this.validationService = validationService;
        this.allowSelfApproval = allowSelfApproval;
    }

    @Transactional(readOnly = true)
    public ProcessingJobFullDetailsDto getJobFullDetails(UUID jobId) {
        return getJobFullDetails(jobId, null);
    }

    @Transactional(readOnly = true)
    public ProcessingJobFullDetailsDto getJobFullDetails(UUID jobId, PoUserPrincipal principal) {
        ProcessingJob job = resolveJobWithTenant(jobId, principal);
        ExtractedPurchaseOrder po = poRepository.findByProcessingJobId(jobId).orElse(null);
        UUID poId = po != null ? po.getId() : null;

        return new ProcessingJobFullDetailsDto(
                ProcessingJobResponseDto.from(job),
                po,
                poId != null ? lineItemRepository.findByPurchaseOrderIdOrderByLineNumberAsc(poId) : null,
                poId != null ? patientRepository.findByPurchaseOrderId(poId).orElse(null) : null,
                poId != null ? billingRepository.findByPurchaseOrderId(poId).orElse(null) : null,
                poId != null ? shippingRepository.findByPurchaseOrderId(poId).orElse(null) : null,
                docAnalysisRepository.findByProcessingJobId(jobId),
                poId != null ? comparisonRepository.findByPurchaseOrderId(poId).orElse(null) : null,
                poId != null ? validationRepository.findByPurchaseOrderId(poId) : null,
                duplicateRepository.findByProcessingJobId(jobId).map(java.util.List::of).orElse(java.util.List.of()),
                poId != null ? editHistoryRepository.findByPurchaseOrderIdOrderByEditedAtDesc(poId) : null,
                poId != null ? approvalHistoryRepository.findByPurchaseOrderIdOrderByActionAtAsc(poId) : null
        );
    }

    @Transactional
    public void updateField(UUID jobId, FieldUpdateRequestDto req, String strEditor) {
        updateField(jobId, req, null, strEditor);
    }

    @Transactional
    public void updateField(UUID jobId, FieldUpdateRequestDto req, PoUserPrincipal principal, String strFallbackUser) {
        ProcessingJob job = resolveJobWithTenant(jobId, principal);
        ExtractedPurchaseOrder po = poRepository.findByProcessingJobId(job.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PO not found for job: " + jobId));

        if (po.isApproved()) {
            throw new IllegalStateException("Approved purchase orders are read-only and cannot be modified.");
        }

        String strUser = (principal != null) ? principal.getUsername() : strFallbackUser;
        String strOldValue = applyFieldChange(po, req.fieldName(), req.newValue());

        po.setWorkflowStatus(PoWorkflowStatus.EDITED);
        po.setVerifiedBy(null);
        po.setVerifiedAt(null);
        poRepository.save(po);

        PoFieldEditHistory audit = new PoFieldEditHistory(
                po, req.fieldName(), strOldValue, req.newValue(), strUser, req.reason()
        );
        editHistoryRepository.save(audit);
        log.info("Field [{}] updated on PO [{}] by [{}]", req.fieldName(), po.getPoNumber(), strUser);
    }

    @Transactional
    public void verifyJob(UUID jobId, String strUser, String strComments) {
        verifyJob(jobId, null, strUser, strComments);
    }

    @Transactional
    public void verifyJob(UUID jobId, PoUserPrincipal principal, String strFallbackUser, String strComments) {
        ProcessingJob job = resolveJobWithTenant(jobId, principal);
        ExtractedPurchaseOrder po = poRepository.findByProcessingJobId(job.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PO not found for job: " + jobId));

        ValidationSummaryDto validation = validationService.validatePurchaseOrder(po);
        if (!validation.isValid()) {
            throw new IllegalStateException("Verification rejected: " + validation.errorCount() + " validation error(s) present.");
        }

        String strUser = (principal != null) ? principal.getUsername() : strFallbackUser;
        po.setWorkflowStatus(PoWorkflowStatus.VERIFIED);
        po.setVerifiedBy(strUser);
        po.setVerifiedAt(Instant.now());
        poRepository.save(po);

        PoApprovalHistory history = new PoApprovalHistory(po, "VERIFIED", strUser, strComments);
        approvalHistoryRepository.save(history);
        log.info("PO [{}] verified by [{}]", po.getPoNumber(), strUser);
    }

    @Transactional
    public void approveJob(UUID jobId, String strUser, String strComments) {
        approveJob(jobId, null, strUser, strComments);
    }

    @Transactional
    public void approveJob(UUID jobId, PoUserPrincipal principal, String strFallbackUser, String strComments) {
        ProcessingJob job = resolveJobWithTenant(jobId, principal);
        ExtractedPurchaseOrder po = poRepository.findByProcessingJobId(job.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PO not found for job: " + jobId));

        if (po.getWorkflowStatus() != PoWorkflowStatus.VERIFIED) {
            throw new IllegalStateException("PO must be in VERIFIED status before approval. Current status: " + po.getWorkflowStatus());
        }

        String strUser = (principal != null) ? principal.getUsername() : strFallbackUser;
        if (!allowSelfApproval && strUser.equalsIgnoreCase(po.getVerifiedBy())) {
            throw new IllegalStateException("Policy violation: self-approval is not permitted. The verifier cannot also approve the purchase order.");
        }

        po.setWorkflowStatus(PoWorkflowStatus.APPROVED);
        po.setApproved(true);
        po.setSuperseded(false);
        po.setApprovedBy(strUser);
        po.setApprovedAt(Instant.now());
        poRepository.save(po);

        // Transactionally supersede previous approved revisions for this PO
        java.util.List<ExtractedPurchaseOrder> alPriorRevisions = poRepository
                .findByTenantIdAndPoNumberAndIsApprovedTrueAndIsSupersededFalse(po.getTenantId(), po.getPoNumber());

        for (ExtractedPurchaseOrder priorPo : alPriorRevisions) {
            if (!priorPo.getId().equals(po.getId())) {
                priorPo.setSuperseded(true);
                priorPo.setSupersededById(po.getId());
                priorPo.setSupersededAt(Instant.now());
                priorPo.setWorkflowStatus(PoWorkflowStatus.SUPERSEDED);
                poRepository.save(priorPo);

                PoApprovalHistory superHistory = new PoApprovalHistory(
                        priorPo,
                        "SUPERSEDED",
                        strUser,
                        "Superseded by approved Revision " + po.getRevisionNumber() + " (ID: " + po.getId() + ")"
                );
                approvalHistoryRepository.save(superHistory);
                log.info("PO Revision [{}] (ID: {}) superseded by Revision [{}]",
                        priorPo.getRevisionNumber(), priorPo.getId(), po.getRevisionNumber());
            }
        }

        job.setProcessingStatus(JobProcessingStatus.COMPLETED);
        jobRepository.save(job);

        PoApprovalHistory history = new PoApprovalHistory(po, "APPROVED", strUser, strComments);
        approvalHistoryRepository.save(history);
        log.info("PO [{}] APPROVED by [{}]", po.getPoNumber(), strUser);
    }

    @Transactional
    public void rejectJob(UUID jobId, String strUser, String strComments) {
        rejectJob(jobId, null, strUser, strComments);
    }

    @Transactional
    public void rejectJob(UUID jobId, PoUserPrincipal principal, String strFallbackUser, String strComments) {
        ProcessingJob job = resolveJobWithTenant(jobId, principal);
        ExtractedPurchaseOrder po = poRepository.findByProcessingJobId(job.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PO not found for job: " + jobId));

        String strUser = (principal != null) ? principal.getUsername() : strFallbackUser;
        po.setWorkflowStatus(PoWorkflowStatus.REJECTED);
        po.setApproved(false);
        poRepository.save(po);

        PoApprovalHistory history = new PoApprovalHistory(po, "REJECTED", strUser, strComments);
        approvalHistoryRepository.save(history);
        log.info("PO [{}] REJECTED by [{}]", po.getPoNumber(), strUser);
    }

    @Transactional
    public void reExtractJob(UUID jobId) {
        reExtractJob(jobId, null);
    }

    @Transactional
    public void reExtractJob(UUID jobId, PoUserPrincipal principal) {
        ProcessingJob job = resolveJobWithTenant(jobId, principal);
        job.setProcessingStatus(JobProcessingStatus.QUEUED);
        job.getWorkerLease().setLockedBy(null);
        job.getWorkerLease().setLockExpiresAt(null);
        job.getWorkerLease().setRetryCount(0);
        job.setLastError(null);
        jobRepository.save(job);
        log.info("Job [{}] requeued for re-extraction", jobId);
    }

    private ProcessingJob resolveJobWithTenant(UUID jobId, PoUserPrincipal principal) {
        if (principal != null && !principal.isGlobalAdmin() && principal.getTenantId() != null) {
            return jobRepository.findByIdAndTenantId(jobId, principal.getTenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Processing job not found: " + jobId));
        }
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found: " + jobId));
    }

    private String applyFieldChange(ExtractedPurchaseOrder po, String strField, String strNewVal) {
        String strOld = null;
        switch (strField) {
            case "poNumber" -> { strOld = po.getPoNumber(); po.setPoNumber(strNewVal); }
            case "currency" -> { strOld = po.getCurrency(); po.setCurrency(strNewVal); }
            case "poDate" -> { strOld = String.valueOf(po.getPoDate()); po.setPoDate(LocalDate.parse(strNewVal)); }
            case "subtotal" -> { strOld = String.valueOf(po.getSubtotal()); po.setSubtotal(new BigDecimal(strNewVal)); }
            case "grandTotal" -> { strOld = String.valueOf(po.getGrandTotal()); po.setGrandTotal(new BigDecimal(strNewVal)); }
            case "vendorName" -> { strOld = po.getVendorName(); po.setVendorName(strNewVal); }
            case "paymentTerms" -> { strOld = po.getPaymentTerms(); po.setPaymentTerms(strNewVal); }
            default -> log.warn("Unrecognized field name for direct PO update: {}", strField);
        }
        return strOld;
    }
}
