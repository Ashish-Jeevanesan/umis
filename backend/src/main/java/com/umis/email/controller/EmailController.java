package com.umis.email.controller;

import com.umis.common.api.ApiResponse;
import com.umis.common.api.PageResponse;
import com.umis.email.dto.AttachmentDto;
import com.umis.email.dto.EmailDetailDto;
import com.umis.email.dto.EmailDto;
import com.umis.email.dto.ManualSyncRequest;
import com.umis.email.model.IngestionJob;
import com.umis.email.model.IngestionStatus;
import com.umis.email.model.TriggerType;
import com.umis.email.service.EmailQueryService;
import com.umis.email.service.EmailSyncManager;
import com.umis.security.RoleConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for email messages, attachments, and manual synchronization triggers.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Emails", description = "Email message query, attachment download, and sync APIs")
public class EmailController {

    private final EmailQueryService queryService;
    private final EmailSyncManager syncManager;

    public EmailController(EmailQueryService queryService, EmailSyncManager syncManager) {
        this.queryService = queryService;
        this.syncManager = syncManager;
    }

    @GetMapping("/emails")
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "List ingested emails with optional filtering by mailbox and status")
    public ResponseEntity<ApiResponse<PageResponse<EmailDto>>> listEmails(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID mailboxId,
            @RequestParam(required = false) IngestionStatus status,
            @PageableDefault(sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<EmailDto> page = PageResponse.from(queryService.listEmails(tenantId, mailboxId, status, pageable));
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/emails/{id}")
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "Get complete email message details, recipients, and attachments")
    public ResponseEntity<ApiResponse<EmailDetailDto>> getEmail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getEmailById(id)));
    }

    @GetMapping("/emails/{id}/attachments")
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "List attachments for an ingested email")
    public ResponseEntity<ApiResponse<List<AttachmentDto>>> listAttachments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.listAttachments(id)));
    }

    @GetMapping("/emails/{id}/attachments/{attachmentId}")
    @PreAuthorize(RoleConstants.HAS_TENANT_ACCESS)
    @Operation(summary = "Securely download an attachment binary file stream")
    public ResponseEntity<InputStreamResource> downloadAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId
    ) {
        EmailQueryService.DownloadableAttachment download = queryService.getAttachmentDownload(id, attachmentId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.contentLength())
                .body(new InputStreamResource(download.inputStream()));
    }

    @PostMapping("/mailboxes/{id}/sync")
    @PreAuthorize(RoleConstants.HAS_TENANT_ADMIN)
    @Operation(summary = "Trigger immediate manual synchronization for a mailbox")
    public ResponseEntity<ApiResponse<IngestionJob>> triggerManualSync(
            @PathVariable UUID id,
            @RequestBody(required = false) ManualSyncRequest request
    ) {
        TriggerType trigger = (request != null && request.triggerType() != null)
                ? request.triggerType()
                : TriggerType.MANUAL_SYNC;
        IngestionJob job = syncManager.synchronizeMailbox(id, trigger);
        return ResponseEntity.ok(ApiResponse.ok("Synchronization triggered successfully", job));
    }
}
