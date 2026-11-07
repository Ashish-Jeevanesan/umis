package com.umis.email.model;

import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an ingested email message and its lifecycle metadata.
 */
@Entity
@Table(
        name = "email_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mailbox_provider_msg",
                columnNames = {"mailbox_id", "provider_message_id"}
        )
)
public class EmailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mailbox_id", nullable = false)
    private MailboxConnection mailbox;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private MailboxProvider provider;

    @Column(name = "provider_message_id", nullable = false, length = 255)
    private String providerMessageId;

    @Column(name = "thread_id", length = 255)
    private String threadId;

    @Column(name = "internet_message_id", length = 255)
    private String internetMessageId;

    @Column(nullable = false, length = 255)
    private String sender;

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Column(length = 1024)
    private String subject;

    @Column(length = 1024)
    private String snippet;

    @Column(name = "body_text_storage_ref", length = 512)
    private String bodyTextStorageRef;

    @Column(name = "body_html_storage_ref", length = 512)
    private String bodyHtmlStorageRef;

    @Column(name = "body_html_available", nullable = false)
    private boolean bodyHtmlAvailable = false;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false, length = 32)
    private IngestionStatus ingestionStatus = IngestionStatus.INGESTED;

    @Column(name = "ingestion_error_details", length = 2000)
    private String ingestionErrorDetails;

    @Column(name = "has_attachments", nullable = false)
    private boolean hasAttachments = false;

    @Column(name = "attachment_count", nullable = false)
    private int attachmentCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EmailMessage() {
    }

    public EmailMessage(
            UUID tenantId,
            MailboxConnection mailbox,
            MailboxProvider provider,
            String providerMessageId,
            String sender,
            String senderName,
            String subject,
            String snippet,
            String contentHash,
            Instant receivedAt
    ) {
        this.tenantId = tenantId;
        this.mailbox = mailbox;
        this.provider = provider;
        this.providerMessageId = providerMessageId;
        this.sender = sender;
        this.senderName = senderName;
        this.subject = subject;
        this.snippet = snippet;
        this.contentHash = contentHash;
        this.receivedAt = receivedAt;
        this.ingestionStatus = IngestionStatus.INGESTED;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public MailboxConnection getMailbox() {
        return mailbox;
    }

    public void setMailbox(MailboxConnection mailbox) {
        this.mailbox = mailbox;
    }

    public MailboxProvider getProvider() {
        return provider;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getSender() {
        return sender;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSubject() {
        return subject;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getBodyTextStorageRef() {
        return bodyTextStorageRef;
    }

    public void setBodyTextStorageRef(String bodyTextStorageRef) {
        this.bodyTextStorageRef = bodyTextStorageRef;
    }

    public String getBodyHtmlStorageRef() {
        return bodyHtmlStorageRef;
    }

    public void setBodyHtmlStorageRef(String bodyHtmlStorageRef) {
        this.bodyHtmlStorageRef = bodyHtmlStorageRef;
    }

    public boolean isBodyHtmlAvailable() {
        return bodyHtmlAvailable;
    }

    public void setBodyHtmlAvailable(boolean bodyHtmlAvailable) {
        this.bodyHtmlAvailable = bodyHtmlAvailable;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public IngestionStatus getIngestionStatus() {
        return ingestionStatus;
    }

    public void setIngestionStatus(IngestionStatus ingestionStatus) {
        this.ingestionStatus = ingestionStatus;
    }

    public String getIngestionErrorDetails() {
        return ingestionErrorDetails;
    }

    public void setIngestionErrorDetails(String ingestionErrorDetails) {
        this.ingestionErrorDetails = ingestionErrorDetails;
    }

    public boolean isHasAttachments() {
        return hasAttachments;
    }

    public void setHasAttachments(boolean hasAttachments) {
        this.hasAttachments = hasAttachments;
    }

    public int getAttachmentCount() {
        return attachmentCount;
    }

    public void setAttachmentCount(int attachmentCount) {
        this.attachmentCount = attachmentCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
