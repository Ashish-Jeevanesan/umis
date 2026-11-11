package com.po.processor.controller;

import com.po.processor.dto.ApproveJobRequestDto;
import com.po.processor.dto.FieldUpdateRequestDto;
import com.po.processor.dto.PoDashboardSummaryDto;
import com.po.processor.dto.ProcessingJobFullDetailsDto;
import com.po.processor.dto.ProcessingJobResponseDto;
import com.po.processor.dto.VerifyJobRequestDto;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.exception.ResourceNotFoundException;
import com.po.processor.hospital.HospitalMasterService;
import com.po.processor.hospital.dto.HospitalHistoryDto;
import com.po.processor.security.PoUserPrincipal;
import com.po.processor.service.PoProcessingService;
import com.po.processor.service.PoWorkflowService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for inspecting Purchase Order processing jobs, details, human review, and metrics.
 * Secured with stateless JWT authentication and role-based access control.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class ProcessingJobController {

    private final PoProcessingService processingService;
    private final PoWorkflowService workflowService;
    private final HospitalMasterService hospitalMasterService;

    public ProcessingJobController(
            PoProcessingService processingService,
            PoWorkflowService workflowService,
            HospitalMasterService hospitalMasterService
    ) {
        this.processingService = processingService;
        this.workflowService = workflowService;
        this.hospitalMasterService = hospitalMasterService;
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAnyRole('PO_VIEWER', 'PO_REVIEWER', 'PO_VERIFIER', 'PO_APPROVER', 'PO_ADMIN')")
    public ResponseEntity<PoDashboardSummaryDto> getDashboardSummary(
            @AuthenticationPrincipal PoUserPrincipal principal
    ) {
        UUID tenantId = (principal != null && !principal.isGlobalAdmin()) ? principal.getTenantId() : null;
        return ResponseEntity.ok(processingService.getDashboardSummary(tenantId));
    }

    @GetMapping("/processing-jobs")
    @PreAuthorize("hasAnyRole('PO_VIEWER', 'PO_REVIEWER', 'PO_VERIFIER', 'PO_APPROVER', 'PO_ADMIN')")
    public ResponseEntity<Page<ProcessingJobResponseDto>> listJobs(
            @AuthenticationPrincipal PoUserPrincipal principal,
            @RequestParam(required = false) JobProcessingStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = (principal != null && !principal.isGlobalAdmin()) ? principal.getTenantId() : null;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<ProcessingJobResponseDto> resultPage = processingService
                .searchJobs(tenantId, status, search, startDate, endDate, pageable)
                .map(ProcessingJobResponseDto::from);

        return ResponseEntity.ok(resultPage);
    }

    @GetMapping("/processing-jobs/{id}")
    @PreAuthorize("hasAnyRole('PO_VIEWER', 'PO_REVIEWER', 'PO_VERIFIER', 'PO_APPROVER', 'PO_ADMIN')")
    public ResponseEntity<ProcessingJobResponseDto> getJobById(
            @PathVariable UUID id,
            @AuthenticationPrincipal PoUserPrincipal principal
    ) {
        UUID tenantId = (principal != null && !principal.isGlobalAdmin()) ? principal.getTenantId() : null;
        ProcessingJob job = processingService.getJobById(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found with ID: " + id));
        return ResponseEntity.ok(ProcessingJobResponseDto.from(job));
    }

    @GetMapping("/processing-jobs/{id}/progress")
    @PreAuthorize("hasAnyRole('PO_VIEWER', 'PO_REVIEWER', 'PO_VERIFIER', 'PO_APPROVER', 'PO_ADMIN')")
    public ResponseEntity<com.po.processor.dto.JobProgressDto> getJobProgress(
            @PathVariable UUID id,
            @AuthenticationPrincipal PoUserPrincipal principal
    ) {
        UUID tenantId = (principal != null && !principal.isGlobalAdmin()) ? principal.getTenantId() : null;
        ProcessingJob job = processingService.getJobById(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found with ID: " + id));
        return ResponseEntity.ok(com.po.processor.dto.JobProgressDto.from(job));
    }

    @GetMapping({"/processing-jobs/{id}/details", "/processing-jobs/{id}/full-details"})
    @PreAuthorize("hasAnyRole('PO_VIEWER', 'PO_REVIEWER', 'PO_VERIFIER', 'PO_APPROVER', 'PO_ADMIN')")
    public ResponseEntity<ProcessingJobFullDetailsDto> getJobFullDetails(
            @PathVariable UUID id,
            @AuthenticationPrincipal PoUserPrincipal principal
    ) {
        UUID tenantId = (principal != null && !principal.isGlobalAdmin()) ? principal.getTenantId() : null;
        ProcessingJob job = processingService.getJobById(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found with ID: " + id));

        if (job.getProcessingStatus() == JobProcessingStatus.PROCESSING
                || job.getProcessingStatus() == JobProcessingStatus.QUEUED
                || job.getProcessingStatus() == JobProcessingStatus.PENDING) {
            int pct = job.getProgress() != null ? job.getProgress().getProgressPercentage() : 0;
            String stage = job.getProgress() != null ? job.getProgress().getCurrentProcessingStage() : "QUEUED";
            throw new com.po.processor.exception.IncompleteProcessingException(
                    "Purchase order processing is currently underway (" + pct + "%). Review will unlock upon completion.",
                    pct, stage
            );
        }

        return ResponseEntity.ok(workflowService.getJobFullDetails(id, principal));
    }

    @PutMapping("/processing-jobs/{id}/fields")
    @PreAuthorize("hasAnyRole('PO_REVIEWER', 'PO_VERIFIER', 'PO_APPROVER', 'PO_ADMIN')")
    public ResponseEntity<Map<String, String>> updateField(
            @PathVariable UUID id,
            @Valid @RequestBody FieldUpdateRequestDto req,
            @AuthenticationPrincipal PoUserPrincipal principal
    ) {
        workflowService.updateField(id, req, principal, "reviewer");
        return ResponseEntity.ok(Map.of("message", "Field updated successfully"));
    }

    @PostMapping("/processing-jobs/{id}/verify")
    @PreAuthorize("hasAnyRole('PO_VERIFIER', 'PO_APPROVER', 'PO_ADMIN')")
    public ResponseEntity<Map<String, String>> verifyJob(
            @PathVariable UUID id,
            @RequestBody(required = false) VerifyJobRequestDto req,
            @AuthenticationPrincipal PoUserPrincipal principal
    ) {
        String strComments = req != null ? req.comments() : null;
        workflowService.verifyJob(id, principal, "verifier", strComments);
        return ResponseEntity.ok(Map.of("message", "PO verified successfully"));
    }

    @PostMapping("/processing-jobs/{id}/approve")
    @PreAuthorize("hasAnyRole('PO_APPROVER', 'PO_ADMIN')")
    public ResponseEntity<Map<String, String>> approveJob(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveJobRequestDto req,
            @AuthenticationPrincipal PoUserPrincipal principal
    ) {
        String strComments = req != null ? req.comments() : null;
        workflowService.approveJob(id, principal, "approver", strComments);
        return ResponseEntity.ok(Map.of("message", "PO approved successfully"));
    }

    @PostMapping("/processing-jobs/{id}/reject")
    @PreAuthorize("hasAnyRole('PO_APPROVER', 'PO_ADMIN')")
    public ResponseEntity<Map<String, String>> rejectJob(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveJobRequestDto req,
            @AuthenticationPrincipal PoUserPrincipal principal
    ) {
        String strComments = req != null ? req.comments() : null;
        workflowService.rejectJob(id, principal, "approver", strComments);
        return ResponseEntity.ok(Map.of("message", "PO rejected successfully"));
    }

    @PostMapping("/processing-jobs/{id}/re-extract")
    @PreAuthorize("hasAnyRole('PO_ADMIN')")
    public ResponseEntity<Map<String, String>> reExtractJob(
            @PathVariable UUID id,
            @AuthenticationPrincipal PoUserPrincipal principal
    ) {
        workflowService.reExtractJob(id, principal);
        return ResponseEntity.ok(Map.of("message", "Job queued for re-extraction"));
    }

    @GetMapping("/hospitals/{hospitalId}/history")
    @PreAuthorize("hasAnyRole('PO_VIEWER', 'PO_REVIEWER', 'PO_VERIFIER', 'PO_APPROVER', 'PO_ADMIN')")
    public ResponseEntity<HospitalHistoryDto> getHospitalHistory(
            @PathVariable UUID hospitalId,
            @RequestParam(required = false) UUID tenantId,
            @AuthenticationPrincipal PoUserPrincipal principal
    ) {
        UUID effectiveTenant = (principal != null && !principal.isGlobalAdmin())
                ? principal.getTenantId()
                : (tenantId != null ? tenantId : UUID.fromString("11111111-1111-1111-1111-111111111111"));
        return ResponseEntity.ok(hospitalMasterService.getHospitalHistory(effectiveTenant, hospitalId));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "PO-Processor",
                "timestamp", Instant.now()
        ));
    }
}
