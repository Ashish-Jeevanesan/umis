package com.umis.mailbox.controller;

import com.umis.common.api.ApiResponse;
import com.umis.common.api.PageResponse;
import com.umis.mailbox.connector.model.ProviderCapability;
import com.umis.mailbox.connector.model.ProviderConnectionInit;
import com.umis.mailbox.dto.ConnectMailboxRequest;
import com.umis.mailbox.dto.MailboxDto;
import com.umis.mailbox.dto.MailboxHealthDto;
import com.umis.mailbox.dto.UpdateMailboxSettingsRequest;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.service.MailboxService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for mailbox management and provider connectivity.
 */
@RestController
@RequestMapping("/api/v1/mailboxes")
@Tag(name = "Mailboxes", description = "Mailbox connection and configuration APIs")
public class MailboxController {

    private final MailboxService mailboxService;

    public MailboxController(MailboxService mailboxService) {
        this.mailboxService = mailboxService;
    }

    @GetMapping("/providers")
    @Operation(summary = "Get list of supported email providers and capabilities")
    public ResponseEntity<ApiResponse<List<ProviderCapability>>> getProviders() {
        return ResponseEntity.ok(ApiResponse.ok(mailboxService.getProviders()));
    }

    @GetMapping("/oauth/initiate")
    @PreAuthorize(RoleConstants.HAS_TENANT_ADMIN)
    @Operation(summary = "Initiate OAuth connection flow for a provider")
    public ResponseEntity<ApiResponse<ProviderConnectionInit>> initiateOAuth(
            @RequestParam MailboxProvider provider,
            @RequestParam String redirectUri,
            @RequestParam(defaultValue = "default_state") String state,
            @RequestParam(required = false) UUID tenantId
    ) {
        ProviderConnectionInit init = mailboxService.initiateOAuth(provider, redirectUri, state, tenantId);
        return ResponseEntity.ok(ApiResponse.ok(init));
    }

    @PostMapping("/connect")
    @PreAuthorize(RoleConstants.HAS_TENANT_ADMIN)
    @Operation(summary = "Connect a customer mailbox to the organization")
    public ResponseEntity<ApiResponse<MailboxDto>> connectMailbox(@Valid @RequestBody ConnectMailboxRequest request) {
        MailboxDto connected = mailboxService.connectMailbox(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Mailbox connected successfully", connected));
    }

    @GetMapping
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "List connected mailboxes for the tenant")
    public ResponseEntity<ApiResponse<PageResponse<MailboxDto>>> listMailboxes(
            @RequestParam(required = false) UUID tenantId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<MailboxDto> page = PageResponse.from(mailboxService.listMailboxes(tenantId, pageable));
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{id}")
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "Get mailbox details by ID")
    public ResponseEntity<ApiResponse<MailboxDto>> getMailbox(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(mailboxService.getMailboxById(id)));
    }

    @GetMapping("/{id}/health")
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "Test connectivity and check health status of a mailbox")
    public ResponseEntity<ApiResponse<MailboxHealthDto>> checkHealth(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(mailboxService.checkMailboxHealth(id)));
    }

    @PatchMapping("/{id}/settings")
    @PreAuthorize(RoleConstants.HAS_TENANT_ADMIN)
    @Operation(summary = "Update mailbox synchronization settings")
    public ResponseEntity<ApiResponse<MailboxDto>> updateSettings(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMailboxSettingsRequest request
    ) {
        MailboxDto updated = mailboxService.updateSettings(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Settings updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(RoleConstants.HAS_TENANT_ADMIN)
    @Operation(summary = "Disconnect mailbox and stop monitoring")
    public ResponseEntity<ApiResponse<Void>> disconnectMailbox(@PathVariable UUID id) {
        mailboxService.disconnectMailbox(id);
        return ResponseEntity.ok(ApiResponse.ok("Mailbox disconnected successfully", null));
    }
}
