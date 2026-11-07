package com.umis.email.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity storing file attachment metadata and object storage references.
 */
@Entity
@Table(name = "email_attachments")
public class EmailAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    private EmailMessage email;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "storage_reference", nullable = false, length = 512)
    private String storageReference;

    @Column(name = "content_id", length = 255)
    private String contentId;

    @Column(name = "is_inline", nullable = false)
    private boolean isInline = false;

    @Column(name = "scan_status", nullable = false, length = 32)
    private String scanStatus = "CLEAN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public EmailAttachment() {
    }

    public EmailAttachment(
            EmailMessage email,
            UUID tenantId,
            String fileName,
            String contentType,
            long sizeBytes,
            String fileHash,
            String storageReference
    ) {
        this.email = email;
        this.tenantId = tenantId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.fileHash = fileHash;
        this.storageReference = storageReference;
        this.scanStatus = "CLEAN";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public EmailMessage getEmail() {
        return email;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getFileHash() {
        return fileHash;
    }

    public String getStorageReference() {
        return storageReference;
    }

    public String getContentId() {
        return contentId;
    }

    public boolean isInline() {
        return isInline;
    }

    public String getScanStatus() {
        return scanStatus;
    }

    public void setScanStatus(String scanStatus) {
        this.scanStatus = scanStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
