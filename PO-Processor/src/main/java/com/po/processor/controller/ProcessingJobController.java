package com.po.processor.controller;

import com.po.processor.dto.PoDashboardSummaryDto;
import com.po.processor.dto.ProcessingJobResponseDto;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.exception.ResourceNotFoundException;
import com.po.processor.service.PoProcessingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for inspecting Purchase Order processing jobs, dashboard metrics, and health.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class ProcessingJobController {

    private final PoProcessingService processingService;

    public ProcessingJobController(PoProcessingService processingService) {
        this.processingService = processingService;
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<PoDashboardSummaryDto> getDashboardSummary() {
        return ResponseEntity.ok(processingService.getDashboardSummary());
    }

    @GetMapping("/processing-jobs")
    public ResponseEntity<Page<ProcessingJobResponseDto>> listJobs(
            @RequestParam(required = false) JobProcessingStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<ProcessingJobResponseDto> resultPage = processingService
                .searchJobs(status, search, startDate, endDate, pageable)
                .map(ProcessingJobResponseDto::from);

        return ResponseEntity.ok(resultPage);
    }

    @GetMapping("/processing-jobs/{id}")
    public ResponseEntity<ProcessingJobResponseDto> getJobById(@PathVariable UUID id) {
        ProcessingJob job = processingService.getJobById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found with ID: " + id));
        return ResponseEntity.ok(ProcessingJobResponseDto.from(job));
    }

    @GetMapping("/processing-jobs/email/{emailId}")
    public ResponseEntity<ProcessingJobResponseDto> getJobByEmailId(@PathVariable UUID emailId) {
        ProcessingJob job = processingService.getJobByEmailId(emailId)
                .orElseThrow(() -> new ResourceNotFoundException("Processing job not found for email: " + emailId));
        return ResponseEntity.ok(ProcessingJobResponseDto.from(job));
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
