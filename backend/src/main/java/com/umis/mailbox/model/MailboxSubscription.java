package com.umis.mailbox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity tracking provider push subscriptions (Microsoft Graph webhook subscriptions, Gmail watch).
 */
@Entity
@Table(name = "mailbox_subscriptions")
public class MailboxSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mailbox_id", nullable = false)
    private MailboxConnection mailbox;

    @Column(name = "subscription_id", nullable = false, unique = true, length = 255)
    private String subscriptionId;

    @Column(name = "client_state", length = 255)
    private String clientState;

    @Column(name = "notification_url", length = 1024)
    private String notificationUrl;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MailboxSubscription() {
    }

    public MailboxSubscription(
            MailboxConnection mailbox,
            String subscriptionId,
            String clientState,
            String notificationUrl,
            Instant expiresAt
    ) {
        this.mailbox = mailbox;
        this.subscriptionId = subscriptionId;
        this.clientState = clientState;
        this.notificationUrl = notificationUrl;
        this.expiresAt = expiresAt;
        this.status = "ACTIVE";
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

    public MailboxConnection getMailbox() {
        return mailbox;
    }

    public void setMailbox(MailboxConnection mailbox) {
        this.mailbox = mailbox;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getClientState() {
        return clientState;
    }

    public void setClientState(String clientState) {
        this.clientState = clientState;
    }

    public String getNotificationUrl() {
        return notificationUrl;
    }

    public void setNotificationUrl(String notificationUrl) {
        this.notificationUrl = notificationUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
