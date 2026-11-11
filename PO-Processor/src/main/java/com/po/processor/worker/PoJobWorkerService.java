package com.po.processor.worker;

import com.po.processor.ai.DocumentIntelligenceProvider;
import com.po.processor.ai.dto.DocumentExtractionRequest;
import com.po.processor.ai.dto.ExtractedPurchaseOrderDto;
import com.po.processor.comparison.BodyAttachmentComparator;
import com.po.processor.comparison.dto.ComparisonResultDto;
import com.po.processor.document.AttachmentProcessingService;
import com.po.processor.document.EmailBodyExtractor;
import com.po.processor.duplicate.DuplicateDetectionService;
import com.po.processor.duplicate.dto.DuplicateCheckResult;
import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.Hospital;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.PoBodyAttachmentComparison;
import com.po.processor.entity.PoExtractionRun;
import com.po.processor.entity.PoWorkflowStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.hospital.HospitalMasterService;
import com.po.processor.repository.PoBodyAttachmentComparisonRepository;
import com.po.processor.repository.PoExtractionRunRepository;
import com.po.processor.repository.ProcessingJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Database-backed worker claiming and executing durable PO processing tasks.
 */
@Service
public class PoJobWorkerService {

    private static final Logger log = LoggerFactory.getLogger(PoJobWorkerService.class);
    private static final int MAX_BATCH_SIZE = 5;

    private final ProcessingJobRepository jobRepository;
    private final EmailBodyExtractor emailBodyExtractor;
    private final AttachmentProcessingService attachmentProcessingService;
    private final DocumentIntelligenceProvider documentProvider;
    private final PoPersistenceHelper persistenceHelper;
    private final PoExtractionRunRepository extractionRunRepository;
    private final HospitalMasterService hospitalMasterService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final BodyAttachmentComparator bodyComparator;
    private final PoBodyAttachmentComparisonRepository comparisonRepository;
    private final com.po.processor.validation.PoValidationService validationService;
    private final String strWorkerNodeId;
    private final int maxRetries;

    public PoJobWorkerService(
            ProcessingJobRepository jobRepository,
            EmailBodyExtractor emailBodyExtractor,
            AttachmentProcessingService attachmentProcessingService,
            DocumentIntelligenceProvider documentProvider,
            PoPersistenceHelper persistenceHelper,
            PoExtractionRunRepository extractionRunRepository,
            HospitalMasterService hospitalMasterService,
            DuplicateDetectionService duplicateDetectionService,
            BodyAttachmentComparator bodyComparator,
            PoBodyAttachmentComparisonRepository comparisonRepository,
            com.po.processor.validation.PoValidationService validationService,
            @Value("${po.worker.max-retries:3}") int maxRetries
    ) {
        this.jobRepository = jobRepository;
        this.emailBodyExtractor = emailBodyExtractor;
        this.attachmentProcessingService = attachmentProcessingService;
        this.documentProvider = documentProvider;
        this.persistenceHelper = persistenceHelper;
        this.extractionRunRepository = extractionRunRepository;
        this.hospitalMasterService = hospitalMasterService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.bodyComparator = bodyComparator;
        this.comparisonRepository = comparisonRepository;
        this.validationService = validationService;
        this.strWorkerNodeId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.maxRetries = maxRetries;
    }

    /**
     * Polls the database periodically for queued jobs or expired processing leases.
     */
    @Scheduled(fixedDelayString = "${po.worker.poll-interval-ms:3000}")
    public void pollAndProcessJobs() {
        Instant now = Instant.now();
        List<ProcessingJob> alClaimable = jobRepository.findClaimableJobs(
                JobProcessingStatus.QUEUED, JobProcessingStatus.PROCESSING, now, PageRequest.of(0, MAX_BATCH_SIZE)
        );

        for (ProcessingJob job : alClaimable) {
            try {
                claimAndExecute(job.getId());
            } catch (Exception exception) {
                log.error("Unhandled worker error processing job ID: {}", job.getId(), exception);
            }
        }
    }

    /**
     * Acquires lease and executes document understanding on the claimed job.
     *
     * @param jobId Identifier of the processing job
     */
    @Transactional
    public void claimAndExecute(UUID jobId) {
        ProcessingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !acquireLease(job)) {
            return;
        }

        Instant startInstant = Instant.now();
        try {
            log.info("Worker [{}] processing job [{}] (email: {})", strWorkerNodeId, job.getId(), job.getUmisEmailId());

            updateProgress(job, "RETRIEVING_EMAIL_CONTENT", 5, "Retrieving email text content");
            String strBodyRef = job.getBodyStorageRef();
            if ((strBodyRef == null || strBodyRef.isBlank()) && job.getUmisEmailId() != null) {
                strBodyRef = "emails/" + job.getTenantId() + "/" + job.getMailboxId() + "/" + job.getUmisEmailId() + "/body.txt";
            }
            String strEmailText = emailBodyExtractor.extractEmailBodyText(strBodyRef, null);

            updateProgress(job, "RETRIEVING_ATTACHMENTS", 15, "Streaming and inspecting attachments");
            List<DocumentExtractionRequest.DocumentAttachmentInput> alAttInputs =
                    attachmentProcessingService.processJobAttachments(job);

            updateProgress(job, "EXTRACTING_DOCUMENT_TEXT", 30, "Document text analyzed");
            String strOverallMethod = alAttInputs.isEmpty() ? "EMAIL_BODY_TEXT_EXTRACTION" : "DIGITAL_TEXT_EXTRACTION";
            for (DocumentExtractionRequest.DocumentAttachmentInput attInput : alAttInputs) {
                if ("IMAGE_VISION_EXTRACTION".equals(attInput.extractionMethod())) {
                    strOverallMethod = "IMAGE_VISION_EXTRACTION";
                    break;
                }
            }

            DocumentExtractionRequest req = new DocumentExtractionRequest(
                    job.getId(), job.getTenantId(), job.getEmailSubject(),
                    job.getEmailSender(), strEmailText, alAttInputs
            );

            updateProgress(job, "AI_EXTRACTION", 65, "Extracting purchase order details with Gemini AI");
            ExtractedPurchaseOrderDto extractedDto = documentProvider.extractPurchaseOrder(req);
            long durationMs = Duration.between(startInstant, Instant.now()).toMillis();
            recordExtractionRun(job.getId(), durationMs, "SUCCESS", null);

            // Duplicate Detection
            DuplicateCheckResult dupResult = duplicateDetectionService.evaluateDuplicate(
                    job.getId(), job.getTenantId(), extractedDto
            );
            if (dupResult.isDuplicate()) {
                updateProgress(job, "COMPLETED", 100, "Duplicate purchase order identified");
                handleDuplicateJob(job, dupResult, strOverallMethod);
                return;
            }

            updateProgress(job, "EXTRACTING_LINE_ITEMS", 80, "Processing line items, pricing, and hospital accounts");
            Hospital hospital = hospitalMasterService.resolveOrCreateHospital(
                    job.getTenantId(), extractedDto.hospitalName(), extractedDto.hospitalCode()
            );

            updateProgress(job, "VALIDATING_PO", 90, "Reconciling totals and running balance equations");
            ExtractedPurchaseOrder savedPo = persistenceHelper.persistExtractedData(job, extractedDto, hospital, dupResult);
            com.po.processor.validation.dto.ValidationSummaryDto valSummary =
                    validationService.validatePurchaseOrder(savedPo);

            ComparisonResultDto compRes = bodyComparator.compare(strEmailText, extractedDto, !alAttInputs.isEmpty());
            comparisonRepository.findByPurchaseOrderId(savedPo.getId()).ifPresent(comparisonRepository::delete);
            PoBodyAttachmentComparison compEntity = new PoBodyAttachmentComparison(
                    savedPo, job.getId(), compRes.comparisonStatus(), compRes.comparisonReportJson(), compRes.conflictCount()
            );
            comparisonRepository.save(compEntity);

            boolean isIncomplete = savedPo.getGrandTotal() == null
                    || savedPo.getGrandTotal().compareTo(BigDecimal.ZERO) <= 0
                    || savedPo.getPoDate() == null;

            if (compRes.conflictCount() > 0 || isIncomplete || !valSummary.isValid() || valSummary.warningCount() > 0) {
                savedPo.setWorkflowStatus(PoWorkflowStatus.NEEDS_REVIEW);
            }

            updateProgress(job, "PERSISTING_RESULTS", 95, "Finalizing purchase order records");
            updateJobSummary(job, savedPo, strOverallMethod);
            job.setProcessingStatus(JobProcessingStatus.READY_FOR_REVIEW);
            job.setCompletedAt(Instant.now());
            updateProgress(job, "COMPLETED", 100, "Processing complete and ready for review");
            releaseLease(job);
            jobRepository.save(job);
            log.info("Job [{}] completed as PO [{}] with status [{}]", job.getId(), savedPo.getPoNumber(), savedPo.getWorkflowStatus());
        } catch (Exception exception) {
            handleProcessingFailure(job, exception, startInstant);
        }
    }

    private void handleDuplicateJob(ProcessingJob job, DuplicateCheckResult dupResult, String strMethod) {
        ExtractedPurchaseOrder origPo = dupResult.originalPurchaseOrder();
        job.setProcessingStatus(JobProcessingStatus.DUPLICATE);
        job.setCompletedAt(Instant.now());
        if (job.getExtractedSummary() == null) {
            job.setExtractedSummary(new com.po.processor.entity.JobExtractedSummary());
        }
        job.getExtractedSummary().setDuplicateOfId(origPo != null ? origPo.getId() : null);
        job.getExtractedSummary().setPoNumber(origPo != null ? origPo.getPoNumber() : null);
        job.getExtractedSummary().setHospitalName(origPo != null && origPo.getHospital() != null ? origPo.getHospital().getNormalizedName() : null);
        job.getExtractedSummary().setVendorName(origPo != null ? origPo.getVendorName() : null);
        job.getExtractedSummary().setTotalAmount(origPo != null ? origPo.getGrandTotal() : null);
        job.getExtractedSummary().setCurrency(origPo != null ? origPo.getCurrency() : null);
        job.getExtractedSummary().setExtractionMethod(strMethod);
        releaseLease(job);
        jobRepository.save(job);
        log.info("Job [{}] marked as DUPLICATE of PO [{}]", job.getId(), origPo != null ? origPo.getPoNumber() : "unknown");
    }

    private boolean acquireLease(ProcessingJob job) {
        Instant now = Instant.now();
        if (job.getProcessingStatus() == JobProcessingStatus.PROCESSING && !job.getWorkerLease().isExpired(now)) {
            return false;
        }
        job.setProcessingStatus(JobProcessingStatus.PROCESSING);
        job.setStartedAt(now);
        job.getWorkerLease().setLockedBy(strWorkerNodeId);
        job.getWorkerLease().setLockedAt(now);
        job.getWorkerLease().setLockExpiresAt(now.plus(Duration.ofMinutes(5)));
        jobRepository.save(job);
        return true;
    }

    private void releaseLease(ProcessingJob job) {
        job.getWorkerLease().setLockedBy(null);
        job.getWorkerLease().setLockExpiresAt(null);
    }

    private void updateJobSummary(ProcessingJob job, ExtractedPurchaseOrder po, String strMethod) {
        if (job.getExtractedSummary() == null) {
            job.setExtractedSummary(new com.po.processor.entity.JobExtractedSummary());
        }
        job.getExtractedSummary().setPoNumber(po.getPoNumber());
        job.getExtractedSummary().setHospitalName(po.getHospital() != null ? po.getHospital().getNormalizedName() : null);
        job.getExtractedSummary().setVendorName(po.getVendorName());
        job.getExtractedSummary().setTotalAmount(po.getGrandTotal());
        job.getExtractedSummary().setCurrency(po.getCurrency());
        job.getExtractedSummary().setExtractionMethod(strMethod);
    }

    private void updateProgress(ProcessingJob job, String stage, int percentage, String message) {
        if (job.getProgress() == null) {
            job.setProgress(new com.po.processor.entity.JobProcessingProgress());
        }
        job.getProgress().update(stage, percentage, message);
        jobRepository.save(job);
    }

    private void handleProcessingFailure(ProcessingJob job, Exception exception, Instant startInstant) {
        long durationMs = Duration.between(startInstant, Instant.now()).toMillis();
        recordExtractionRun(job.getId(), durationMs, "FAILED", exception.getMessage());

        boolean isPermanent = isPermanentFailure(exception);
        int currentRetries = job.getWorkerLease().getRetryCount() + 1;
        job.getWorkerLease().setRetryCount(currentRetries);
        job.setLastError(exception.getMessage());

        if (job.getProgress() == null) {
            job.setProgress(new com.po.processor.entity.JobProcessingProgress());
        }

        if (!isPermanent && currentRetries < maxRetries) {
            long backoffSeconds = (long) Math.pow(2, currentRetries) * 5;
            job.getWorkerLease().setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
            job.setProcessingStatus(JobProcessingStatus.QUEUED);
            job.getProgress().update("RETRYING", job.getProgress().getProgressPercentage(),
                    "Retrying (attempt " + currentRetries + "): " + exception.getMessage());
            log.warn("Job [{}] transient failure on attempt {}/{}. Requeuing with {}s backoff: {}",
                    job.getId(), currentRetries, maxRetries, backoffSeconds, exception.getMessage());
        } else {
            job.setProcessingStatus(JobProcessingStatus.FAILED);
            job.getProgress().update("FAILED", job.getProgress().getProgressPercentage(),
                    "Failed: " + exception.getMessage());
            log.error("Job [{}] permanently failed (attempts: {}, permanent: {}): {}",
                    job.getId(), currentRetries, isPermanent, exception.getMessage());
        }
        releaseLease(job);
        jobRepository.save(job);
    }

    private boolean isPermanentFailure(Exception exception) {
        if (exception instanceof com.po.processor.exception.AttachmentSizeLimitExceededException) return true;
        if (exception instanceof com.po.processor.exception.GeminiResponseTruncatedException) return true;
        if (exception instanceof org.springframework.dao.DataIntegrityViolationException) return true;
        if (exception instanceof IllegalArgumentException) return true;
        return false;
    }

    private void recordExtractionRun(UUID jobId, long durationMs, String strStatus, String strError) {
        PoExtractionRun run = new PoExtractionRun(jobId, documentProvider.getModelName(), "v1.0");
        run.setExecutionDurationMs(durationMs);
        run.setStatus(strStatus);
        run.setErrorMessage(strError);
        extractionRunRepository.save(run);
    }
}
