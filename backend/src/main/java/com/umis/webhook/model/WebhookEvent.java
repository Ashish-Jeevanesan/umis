package com.umis.webhook.model;

import com.umis.mailbox.model.MailboxConnection;
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
 * Entity logging received provider webhook notifications for auditing and asynchronous processing.
 */
@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(name = "subscription_id", length = 255)
    private String subscriptionId;

    @Column(name = "event_type", length = 128)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mailbox_id")
    private MailboxConnection mailbox;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(columnDefinition = "TEXT")
    private String headers;

    @Column(nullable = false, length = 32)
    private String status = "PROCESSED";

    @Column(name = "error_details", length = 2000)
    private String errorDetails;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public WebhookEvent() {
    }

    public WebhookEvent(String provider, String subscriptionId, String eventType, String payload, String headers) {
        this.provider = provider;
        this.subscriptionId = subscriptionId;
        this.eventType = eventType;
        this.payload = payload;
        this.headers = headers;
        this.status = "PROCESSED";
        this.receivedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getEventType() {
        return eventType;
    }

    public MailboxConnection getMailbox() {
        return mailbox;
    }

    public void setMailbox(MailboxConnection mailbox) {
        this.mailbox = mailbox;
    }

    public String getPayload() {
        return payload;
    }

    public String getHeaders() {
        return headers;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
