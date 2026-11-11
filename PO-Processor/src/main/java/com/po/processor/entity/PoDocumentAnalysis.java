package com.po.processor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing document classification and technical extraction diagnostics for an attachment.
 */
@Entity
@Table(name = "po_document_analyses", schema = "po_processor")
public class PoDocumentAnalysis {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "processing_job_id", nullable = false)
    private UUID processingJobId;

    @Column(name = "processing_job_attachment_id")
    private UUID processingJobAttachmentId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "page_count")
    private int pageCount = 1;

    @Column(name = "document_classification", nullable = false, length = 50)
    private String documentClassification;

    @Column(name = "extraction_method", nullable = false, length = 50)
    private String extractionMethod;

    @Column(name = "extraction_engine", length = 100)
    private String extractionEngine;

    @Column(name = "ocr_used", nullable = false)
    private boolean ocrUsed = false;

    @Column(name = "processing_result", nullable = false, length = 50)
    private String processingResult;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Column(name = "extracted_text_preview", columnDefinition = "TEXT")
    private String extractedTextPreview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PoDocumentAnalysis() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public PoDocumentAnalysis(UUID processingJobId, String strOriginalFilename, String strClassification) {
        this();
        this.processingJobId = processingJobId;
        this.originalFilename = strOriginalFilename;
        this.documentClassification = strClassification;
        this.extractionMethod = "DIGITAL_TEXT_EXTRACTION";
        this.processingResult = "SUCCESS";
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProcessingJobId() { return processingJobId; }
    public void setProcessingJobId(UUID processingJobId) { this.processingJobId = processingJobId; }
    public UUID getProcessingJobAttachmentId() { return processingJobAttachmentId; }
    public void setProcessingJobAttachmentId(UUID processingJobAttachmentId) { this.processingJobAttachmentId = processingJobAttachmentId; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String strOriginalFilename) { this.originalFilename = strOriginalFilename; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String strMimeType) { this.mimeType = strMimeType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String strFileHash) { this.fileHash = strFileHash; }
    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }
    public String getDocumentClassification() { return documentClassification; }
    public void setDocumentClassification(String strDocumentClassification) { this.documentClassification = strDocumentClassification; }
    public String getExtractionMethod() { return extractionMethod; }
    public void setExtractionMethod(String strExtractionMethod) { this.extractionMethod = strExtractionMethod; }
    public String getExtractionEngine() { return extractionEngine; }
    public void setExtractionEngine(String strExtractionEngine) { this.extractionEngine = strExtractionEngine; }
    public boolean isOcrUsed() { return ocrUsed; }
    public void setOcrUsed(boolean ocrUsed) { this.ocrUsed = ocrUsed; }
    public String getProcessingResult() { return processingResult; }
    public void setProcessingResult(String strProcessingResult) { this.processingResult = strProcessingResult; }
    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String strErrorDetails) { this.errorDetails = strErrorDetails; }
    public String getExtractedTextPreview() { return extractedTextPreview; }
    public void setExtractedTextPreview(String strExtractedTextPreview) { this.extractedTextPreview = strExtractedTextPreview; }
    public Instant getCreatedAt() { return createdAt; }
}
