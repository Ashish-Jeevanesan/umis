package com.po.processor.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * Unified request payload passed to DocumentIntelligenceProvider containing email context and attachments.
 */
public record DocumentExtractionRequest(
        UUID jobId,
        UUID tenantId,
        String emailSubject,
        String emailSender,
        String emailBodyText,
        List<DocumentAttachmentInput> attachments
) {
    /**
     * Attachment representation for multimodal or text-based extraction.
     */
    public record DocumentAttachmentInput(
            UUID attachmentId,
            String fileName,
            String contentType,
            String extractedText,
            String base64Content,
            int pageCount,
            String extractionMethod
    ) {}
}
