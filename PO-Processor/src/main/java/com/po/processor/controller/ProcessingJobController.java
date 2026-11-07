package com.po.processor.controller;

import com.po.processor.dto.ProcessingJobResponseDto;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.exception.ResourceNotFoundException;
import com.po.processor.service.PoProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for inspecting Purchase Order processing jobs and verifying service health.
 */
@RestController
@RequestMapping("/api/v1")
public class ProcessingJobController {

    private final PoProcessingService processingService;

    public ProcessingJobController(PoProcessingService processingService) {
        this.processingService = processingService;
    }

    @GetMapping("/processing-jobs")
    public ResponseEntity<List<ProcessingJobResponseDto>> listAllJobs() {
        List<ProcessingJobResponseDto> dtoList = processingService.listAllJobs().stream()
                .map(ProcessingJobResponseDto::from)
                .toList();
        return ResponseEntity.ok(dtoList);
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
