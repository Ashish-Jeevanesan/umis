package com.umis.event.consumer.controller;

import com.umis.common.api.ApiResponse;
import com.umis.event.consumer.dto.ProcessingAcknowledgementRequest;
import com.umis.event.consumer.dto.ProcessingStatusDto;
import com.umis.event.consumer.service.ConsumerService;
import com.umis.security.RoleConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for downstream consumer processing acknowledgements and status tracking.
 */
@RestController
@RequestMapping("/api/v1/emails/{emailId}/acknowledgements")
@Tag(name = "Acknowledgements", description = "Downstream consumer processing status and acknowledgement APIs")
public class AcknowledgementController {

    private final ConsumerService consumerService;

    public AcknowledgementController(ConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @PostMapping
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "Submit a processing outcome acknowledgement from a consumer module")
    public ResponseEntity<ApiResponse<ProcessingStatusDto>> acknowledge(
            @PathVariable UUID emailId,
            @Valid @RequestBody ProcessingAcknowledgementRequest request
    ) {
        ProcessingStatusDto status = consumerService.acknowledgeProcessing(emailId, request);
        return ResponseEntity.ok(ApiResponse.ok("Processing outcome recorded", status));
    }

    @GetMapping
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "Get all consumer processing statuses for an email")
    public ResponseEntity<ApiResponse<List<ProcessingStatusDto>>> getStatuses(@PathVariable UUID emailId) {
        return ResponseEntity.ok(ApiResponse.ok(consumerService.getProcessingStatuses(emailId)));
    }
}
