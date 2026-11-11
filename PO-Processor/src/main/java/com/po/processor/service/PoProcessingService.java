package com.po.processor.service;

import com.po.processor.dto.UmisAcknowledgementRequest;
import com.po.processor.dto.UmisProcessingStatus;
import com.po.processor.dto.event.EmailIngestedPayloadDto;
import com.po.processor.dto.event.UmisEventDto;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.ProcessedEvent;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.entity.ProcessingJobAttachment;
import com.po.processor.exception.EventProcessingException;
import com.po.processor.repository.ProcessedEventRepository;
import com.po.processor.repository.ProcessingJobRepository;
import com.po.processor.storage.StorageReader;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core business service orchestrating PO email event validation, storage inspection,
 * processing job lifecycle, and UMIS acknowledgement.
 */
@Service
public class PoProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PoProcessingService.class);

    private final ProcessingJobRepository jobRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final StorageReader storageReader;
    private final UmisClient umisClient;

    public PoProcessingService(
            ProcessingJobRepository jobRepository,
            ProcessedEventRepository processedEventRepository,
            StorageReader storageReader,
            UmisClient umisClient
    ) {
        this.jobRepository = jobRepository;
        this.processedEventRepository = processedEventRepository;
        this.storageReader = storageReader;
        this.umisClient = umisClient;
    }

    /**
     * Ingests and processes an incoming EMAIL_INGESTED event from UMIS.
     */
    @Transactional
    public ProcessingJob processEmailIngestedEvent(UmisEventDto<EmailIngestedPayloadDto> event) {
        validateEvent(event);

        UUID eventId = event.eventId();
        String consumerName = umisClient.getConsumerId();

        // 1. Idempotency guard: check if event was already processed
        if (processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)) {
            log.info("Event {} already processed by consumer {}. Returning existing record.", eventId, consumerName);
            return jobRepository.findByEventId(eventId).orElse(null);
        }

        EmailIngestedPayloadDto payload = event.payload();
        UUID tenantId = payload.tenantId() != null ? payload.tenantId() : event.tenantId();
        UUID emailId = payload.emailId();
        UUID mailboxId = payload.mailboxId() != null ? payload.mailboxId() : event.mailboxId();

        log.info("Initializing PO processing job for email: {}, tenant: {}, event: {}", emailId, tenantId, eventId);

        // 2. Fetch or create processing job
        ProcessingJob job = jobRepository.findByTenantIdAndUmisEmailId(tenantId, emailId)
                .orElseGet(() -> new ProcessingJob(tenantId, emailId, mailboxId, eventId, event.occurredAt()));

        job.setProcessingAttempts(job.getProcessingAttempts() + 1);
        job.setStartedAt(Instant.now());
        job.setEmailSubject(payload.subject());
        job.setEmailSender(payload.sender());
        job.setEmailSenderName(payload.senderName());
        if (payload.recipients() != null && !payload.recipients().isEmpty()) {
            job.setEmailRecipients(String.join(", ", payload.recipients()));
        }

        // 3. UMIS ingests every email in the mailbox with no business filtering (that's
        // intentionally out of its scope), so this is the only layer that can tell a
        // purchase order apart from unrelated mail. Skip anything with no PO indicators
        // in subject/sender/attachment names before any storage I/O or extraction is queued.
        if (!looksLikePurchaseOrder(payload)) {
            job.setProcessingStatus(JobProcessingStatus.SKIPPED);
            job.setLastError(null);
            ProcessingJob skippedJob = jobRepository.save(job);
            processedEventRepository.save(new ProcessedEvent(eventId, consumerName));
            sendUmisAcknowledgement(skippedJob, payload, eventId, UmisProcessingStatus.IGNORED,
                    "Email does not appear to be a purchase order (no PO indicators in "
                            + "subject, sender, or attachments); skipped without extraction.");
            return skippedJob;
        }

        job.setProcessingStatus(JobProcessingStatus.PROCESSING);

        // 4. Inspect email body in storage and record pointer
        String strBodyRef = payload.bodyTextStorageRef() != null
                ? payload.bodyTextStorageRef()
                : payload.bodyHtmlStorageRef();
        job.setBodyStorageRef(strBodyRef);
        verifyEmailBody(payload);

        // 5. Process and attach attachment metadata from storage
        processAttachments(job, payload);

        // 6. Transition to QUEUED for database-backed worker claiming
        job.setProcessingStatus(JobProcessingStatus.QUEUED);
        job.setLastError(null);
        ProcessingJob savedJob = jobRepository.save(job);

        // 7. Record idempotency marker
        processedEventRepository.save(new ProcessedEvent(eventId, consumerName));

        // 8. Send downstream acknowledgement to UMIS
        sendUmisAcknowledgement(savedJob, payload, eventId, UmisProcessingStatus.PROCESSING,
                "PO processing job initialized and ready for extraction");

        return savedJob;
    }

    // Mirrors the keyword family DocumentClassificationService already uses for its
    // PURCHASE_ORDER classification, applied earlier (subject/sender/attachment names only,
    // before any content is fetched) so non-PO mail never reaches storage I/O or extraction.
    // Deliberately permissive: a legitimate PO with an unusual subject costs nothing extra
    // today (same path as before this change), while a false "not a PO" would silently drop
    // real business data - so this only skips when NONE of the signals look PO-like.
    private static final List<String> PURCHASE_ORDER_KEYWORDS =
            List.of("purchase_order", "purchase order", "po-", "po_");

    private boolean looksLikePurchaseOrder(EmailIngestedPayloadDto payload) {
        if (containsPurchaseOrderKeyword(payload.subject())
                || containsPurchaseOrderKeyword(payload.sender())
                || containsPurchaseOrderKeyword(payload.senderName())) {
            return true;
        }
        List<EmailIngestedPayloadDto.AttachmentReferenceDto> attachments = payload.attachments();
        if (attachments != null) {
            for (EmailIngestedPayloadDto.AttachmentReferenceDto attachment : attachments) {
                if (containsPurchaseOrderKeyword(attachment.fileName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsPurchaseOrderKeyword(String value) {
        if (value == null) {
            return false;
        }
        String strLower = value.toLowerCase();
        for (String keyword : PURCHASE_ORDER_KEYWORDS) {
            if (strLower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void validateEvent(UmisEventDto<EmailIngestedPayloadDto> event) {
        if (event == null) {
            throw new EventProcessingException("Event envelope must not be null");
        }
        if (event.eventId() == null) {
            throw new EventProcessingException("Event ID is required in envelope");
        }
        if (!"EMAIL_INGESTED".equalsIgnoreCase(event.eventType())) {
            throw new EventProcessingException("Unsupported event type: " + event.eventType());
        }
        if (event.payload() == null || event.payload().emailId() == null) {
            throw new EventProcessingException("Event payload and email ID are required");
        }
    }

    private void verifyEmailBody(EmailIngestedPayloadDto payload) {
        String bodyRef = payload.bodyTextStorageRef() != null
                ? payload.bodyTextStorageRef()
                : payload.bodyHtmlStorageRef();

        if (bodyRef != null && !storageReader.exists(bodyRef)) {
            log.warn("Email body not found in storage for ref: {}", bodyRef);
        }
    }

    private void processAttachments(ProcessingJob job, EmailIngestedPayloadDto payload) {
        List<EmailIngestedPayloadDto.AttachmentReferenceDto> attachments = payload.attachments();
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        for (EmailIngestedPayloadDto.AttachmentReferenceDto ref : attachments) {
            boolean fileExists = storageReader.exists(ref.storageReference());
            ProcessingJobAttachment attachment = new ProcessingJobAttachment(
                    ref.storageReference(),
                    ref.fileName(),
                    ref.contentType(),
                    ref.size()
            );
            attachment.setFileHash(ref.fileHash());

            if (fileExists) {
                attachment.setProcessingStatus(JobProcessingStatus.READY_FOR_EXTRACTION);
            } else {
                log.warn("Referenced attachment file is not accessible in storage: {}", ref.storageReference());
                attachment.setProcessingStatus(JobProcessingStatus.FAILED);
            }
            job.addAttachment(attachment);
        }
    }

    private void sendUmisAcknowledgement(
            ProcessingJob job,
            EmailIngestedPayloadDto payload,
            UUID eventId,
            UmisProcessingStatus status,
            String message
    ) {
        try {
            UmisAcknowledgementRequest ackRequest = new UmisAcknowledgementRequest(
                    umisClient.getConsumerId(),
                    status,
                    Instant.now(),
                    job.getId().toString(),
                    message,
                    eventId.toString()
            );
            umisClient.acknowledge(payload.emailId(), ackRequest);
        } catch (Exception ex) {
            // Log warning but do not roll back the local job transaction
            log.warn("Failed to deliver UMIS acknowledgement for email {}: {}", payload.emailId(), ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Optional<ProcessingJob> getJobById(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public Optional<ProcessingJob> getJobById(UUID jobId, UUID tenantId) {
        if (tenantId == null) {
            return jobRepository.findById(jobId);
        }
        return jobRepository.findByIdAndTenantId(jobId, tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<ProcessingJob> getJobByEmailId(UUID emailId) {
        return jobRepository.findByUmisEmailId(emailId);
    }

    @Transactional(readOnly = true)
    public List<ProcessingJob> listAllJobs() {
        return jobRepository.findAll();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ProcessingJob> searchJobs(
            JobProcessingStatus status,
            String search,
            Instant startDate,
            Instant endDate,
            org.springframework.data.domain.Pageable pageable
    ) {
        return searchJobs(null, status, search, startDate, endDate, pageable);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ProcessingJob> searchJobs(
            UUID tenantId,
            JobProcessingStatus status,
            String search,
            Instant startDate,
            Instant endDate,
            org.springframework.data.domain.Pageable pageable
    ) {
        Specification<ProcessingJob> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("processingStatus"), status));
            }
            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                Predicate subjectLike = cb.like(cb.lower(root.get("emailSubject")), term);
                Predicate senderLike = cb.like(cb.lower(root.get("emailSender")), term);
                Predicate senderNameLike = cb.like(cb.lower(root.get("emailSenderName")), term);
                predicates.add(cb.or(subjectLike, senderLike, senderNameLike));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("receivedAt"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return jobRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public com.po.processor.dto.PoDashboardSummaryDto getDashboardSummary() {
        return getDashboardSummary(null);
    }

    @Transactional(readOnly = true)
    public com.po.processor.dto.PoDashboardSummaryDto getDashboardSummary(UUID tenantId) {
        long total = tenantId != null ? jobRepository.countByTenantId(tenantId) : jobRepository.count();
        long pending = countStatus(tenantId, JobProcessingStatus.PENDING);
        long queued = countStatus(tenantId, JobProcessingStatus.QUEUED);
        long processing = countStatus(tenantId, JobProcessingStatus.PROCESSING);
        long ready = countStatus(tenantId, JobProcessingStatus.READY_FOR_REVIEW);
        long legacyReady = countStatus(tenantId, JobProcessingStatus.READY_FOR_EXTRACTION);
        long duplicate = countStatus(tenantId, JobProcessingStatus.DUPLICATE);
        long completed = countStatus(tenantId, JobProcessingStatus.COMPLETED);
        long failed = countStatus(tenantId, JobProcessingStatus.FAILED);

        long poIdentified = ready + legacyReady + completed;
        long totalReady = ready + legacyReady;

        return new com.po.processor.dto.PoDashboardSummaryDto(
                total, poIdentified, totalReady, 0, 0, completed, duplicate, failed,
                pending + queued, processing, totalReady, completed
        );
    }

    private long countStatus(UUID tenantId, JobProcessingStatus status) {
        if (tenantId != null) {
            return jobRepository.countByTenantIdAndProcessingStatus(tenantId, status);
        }
        return jobRepository.countByProcessingStatus(status);
    }
}
