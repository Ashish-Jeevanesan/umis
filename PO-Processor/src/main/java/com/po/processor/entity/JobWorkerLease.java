package com.po.processor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

/**
 * Embeddable representing the database-backed worker lease and retry metadata.
 */
@Embeddable
public class JobWorkerLease {

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "lock_expires_at")
    private Instant lockExpiresAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    public JobWorkerLease() {
    }

    /**
     * Checks if the active lease has expired relative to current time.
     *
     * @param now Current timestamp
     * @return true if expired or unheld
     */
    public boolean isExpired(Instant now) {
        if (lockExpiresAt == null) {
            return true;
        }
        return now.isAfter(lockExpiresAt);
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String strLockedBy) {
        this.lockedBy = strLockedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Instant getLockExpiresAt() {
        return lockExpiresAt;
    }

    public void setLockExpiresAt(Instant lockExpiresAt) {
        this.lockExpiresAt = lockExpiresAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }
}
