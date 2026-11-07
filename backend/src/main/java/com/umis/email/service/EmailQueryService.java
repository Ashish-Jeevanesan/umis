package com.umis.email.service;

import com.umis.common.exception.ResourceNotFoundException;
import com.umis.common.exception.UnauthorizedException;
import com.umis.email.dto.AttachmentDto;
import com.umis.email.dto.EmailDetailDto;
import com.umis.email.dto.EmailDto;
import com.umis.email.model.EmailAttachment;
import com.umis.email.model.EmailMessage;
import com.umis.email.model.EmailRecipient;
import com.umis.email.model.IngestionStatus;
import com.umis.email.repository.EmailAttachmentRepository;
import com.umis.email.repository.EmailMessageRepository;
import com.umis.email.repository.EmailRecipientRepository;
import com.umis.security.TenantContext;
import com.umis.tenant.service.TenantService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service providing secure, tenant-isolated email message and attachment queries.
 */
@Service
public class EmailQueryService {

    private final EmailMessageRepository messageRepository;
    private final EmailRecipientRepository recipientRepository;
    private final EmailAttachmentRepository attachmentRepository;
    private final EmailStorageService storageService;
    private final TenantService tenantService;

    public EmailQueryService(
            EmailMessageRepository messageRepository,
            EmailRecipientRepository recipientRepository,
            EmailAttachmentRepository attachmentRepository,
            EmailStorageService storageService,
            TenantService tenantService
    ) {
        this.messageRepository = messageRepository;
        this.recipientRepository = recipientRepository;
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public Page<EmailDto> listEmails(UUID tenantId, UUID mailboxId, IngestionStatus status, Pageable pageable) {
        UUID effectiveTenantId = resolveEffectiveTenantId(tenantId);
        tenantService.validateTenantAccess(effectiveTenantId);

        // effectiveTenantId is only ever null for a GLOBAL_ADMIN who did not pick a specific
        // tenant (resolveEffectiveTenantId always returns a tenant-scoped user's own tenant
        // context, never null) - show emails across all tenants rather than none.
        if (effectiveTenantId == null) {
            if (mailboxId != null) {
                return messageRepository.findByMailboxId(mailboxId, pageable).map(EmailDto::from);
            }
            if (status != null) {
                return messageRepository.findByIngestionStatus(status, pageable).map(EmailDto::from);
            }
            return messageRepository.findAll(pageable).map(EmailDto::from);
        }

        if (mailboxId != null) {
            return messageRepository.findByTenantIdAndMailboxId(effectiveTenantId, mailboxId, pageable)
                    .map(EmailDto::from);
        }
        if (status != null) {
            return messageRepository.findByTenantIdAndIngestionStatus(effectiveTenantId, status, pageable)
                    .map(EmailDto::from);
        }
        return messageRepository.findByTenantId(effectiveTenantId, pageable).map(EmailDto::from);
    }

    @Transactional(readOnly = true)
    public EmailDetailDto getEmailById(UUID emailId) {
        EmailMessage email = messageRepository.findById(emailId)
                .orElseThrow(() -> new ResourceNotFoundException("Email message not found with ID: " + emailId));
        tenantService.validateTenantAccess(email.getTenantId());

        List<String> recipients = recipientRepository.findByEmailId(emailId).stream()
                .map(EmailRecipient::getAddress)
                .toList();
        List<AttachmentDto> attachments = attachmentRepository.findByEmailId(emailId).stream()
                .map(AttachmentDto::from)
                .toList();

        return EmailDetailDto.of(email, recipients, attachments);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listAttachments(UUID emailId) {
        EmailMessage email = messageRepository.findById(emailId)
                .orElseThrow(() -> new ResourceNotFoundException("Email message not found with ID: " + emailId));
        tenantService.validateTenantAccess(email.getTenantId());

        return attachmentRepository.findByEmailId(emailId).stream()
                .map(AttachmentDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DownloadableAttachment getAttachmentDownload(UUID emailId, UUID attachmentId) {
        EmailMessage email = messageRepository.findById(emailId)
                .orElseThrow(() -> new ResourceNotFoundException("Email message not found with ID: " + emailId));
        tenantService.validateTenantAccess(email.getTenantId());

        EmailAttachment attachment = attachmentRepository.findByIdAndEmailId(attachmentId, emailId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with ID: " + attachmentId));

        EmailStorageService.StorageDownload download = storageService.getObject(attachment.getStorageReference());
        return new DownloadableAttachment(
                attachment.getFileName(),
                download.contentType(),
                download.contentLength(),
                download.inputStream()
        );
    }

    private UUID resolveEffectiveTenantId(UUID requestedTenantId) {
        UUID contextId = TenantContext.getTenantId();
        if (contextId != null) {
            if (requestedTenantId != null && !requestedTenantId.equals(contextId)) {
                throw new UnauthorizedException("Access denied: Cannot specify or access another tenant: " + requestedTenantId);
            }
            return contextId;
        }
        return requestedTenantId;
    }

    public record DownloadableAttachment(
            String fileName,
            String contentType,
            long contentLength,
            java.io.InputStream inputStream
    ) {}
}
