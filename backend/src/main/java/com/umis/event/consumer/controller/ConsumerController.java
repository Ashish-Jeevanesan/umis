package com.umis.event.consumer.controller;

import com.umis.common.api.ApiResponse;
import com.umis.common.api.PageResponse;
import com.umis.event.consumer.dto.ConsumerRegistrationDto;
import com.umis.event.consumer.dto.CreateConsumerRequest;
import com.umis.event.consumer.service.ConsumerService;
import com.umis.security.RoleConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for managing external downstream consumer registrations.
 */
@RestController
@RequestMapping("/api/v1/consumers")
@Tag(name = "Consumers", description = "Downstream consumer registration APIs")
public class ConsumerController {

    private final ConsumerService consumerService;

    public ConsumerController(ConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @PostMapping
    @PreAuthorize(RoleConstants.HAS_TENANT_ADMIN)
    @Operation(summary = "Register an external downstream consumer module (e.g. PO_PROCESSING, CRM)")
    public ResponseEntity<ApiResponse<ConsumerRegistrationDto>> registerConsumer(
            @Valid @RequestBody CreateConsumerRequest request
    ) {
        ConsumerRegistrationDto created = consumerService.registerConsumer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Consumer registered successfully", created));
    }

    @GetMapping
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "List registered downstream consumer modules")
    public ResponseEntity<ApiResponse<PageResponse<ConsumerRegistrationDto>>> listConsumers(
            @RequestParam(required = false) UUID tenantId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<ConsumerRegistrationDto> page = PageResponse.from(consumerService.listConsumers(tenantId, pageable));
        return ResponseEntity.ok(ApiResponse.ok(page));
    }
}
