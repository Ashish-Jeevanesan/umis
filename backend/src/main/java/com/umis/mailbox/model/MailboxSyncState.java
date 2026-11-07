package com.umis.mailbox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity maintaining synchronization cursors, history IDs, and concurrency locks per mailbox.
 */
@Entity
@Table(name = "mailbox_sync_state")
public class MailboxSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mailbox_id", nullable = false, unique = true)
    private MailboxConnection mailbox;

    @Column(name = "cursor_value", length = 1024)
    private String cursorValue;

    @Column(name = "last_history_id", length = 255)
    private String lastHistoryId;

    @Column(name = "last_uid")
    private Long lastUid;

    @Column(name = "folder_name", nullable = false, length = 128)
    private String folderName = "INBOX";

    @Column(name = "sync_lock_token", length = 128)
    private String syncLockToken;

    @Column(name = "sync_lock_acquired_at")
    private Instant syncLockAcquiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MailboxSyncState() {
    }

    public MailboxSyncState(MailboxConnection mailbox) {
        this.mailbox = mailbox;
        this.folderName = "INBOX";
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

    public String getCursorValue() {
        return cursorValue;
    }

    public void setCursorValue(String cursorValue) {
        this.cursorValue = cursorValue;
    }

    public String getLastHistoryId() {
        return lastHistoryId;
    }

    public void setLastHistoryId(String lastHistoryId) {
        this.lastHistoryId = lastHistoryId;
    }

    public Long getLastUid() {
        return lastUid;
    }

    public void setLastUid(Long lastUid) {
        this.lastUid = lastUid;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getSyncLockToken() {
        return syncLockToken;
    }

    public void setSyncLockToken(String syncLockToken) {
        this.syncLockToken = syncLockToken;
    }

    public Instant getSyncLockAcquiredAt() {
        return syncLockAcquiredAt;
    }

    public void setSyncLockAcquiredAt(Instant syncLockAcquiredAt) {
        this.syncLockAcquiredAt = syncLockAcquiredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
