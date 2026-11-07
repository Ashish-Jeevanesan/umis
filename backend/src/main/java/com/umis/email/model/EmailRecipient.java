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
 * Entity storing individual recipient addresses for an ingested email.
 */
@Entity
@Table(name = "email_recipients")
public class EmailRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false)
    private EmailMessage email;

    @Column(name = "recipient_type", nullable = false, length = 16)
    private String recipientType; // TO, CC, BCC

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public EmailRecipient() {
    }

    public EmailRecipient(EmailMessage email, String recipientType, String address, String displayName) {
        this.email = email;
        this.recipientType = recipientType;
        this.address = address;
        this.displayName = displayName;
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

    public String getRecipientType() {
        return recipientType;
    }

    public String getAddress() {
        return address;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
