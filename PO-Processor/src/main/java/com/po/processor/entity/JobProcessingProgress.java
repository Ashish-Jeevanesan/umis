package com.po.processor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/**
 * Embeddable value object tracking live percentage progress, stages, and execution timestamps.
 */
@Embeddable
public class JobProcessingProgress {

    @Column(name = "progress_percentage", nullable = false)
    private int progressPercentage = 0;

    @Column(name = "current_processing_stage", nullable = false, length = 50)
    private String currentProcessingStage = "EMAIL_RECEIVED";

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_updated_at")
    private Instant processingUpdatedAt;

    @Column(name = "processing_completed_at")
    private Instant processingCompletedAt;

    @Column(name = "progress_message", length = 255)
    private String progressMessage = "Email received";

    public JobProcessingProgress() {
        this.processingUpdatedAt = Instant.now();
    }

    public void update(String stage, int percentage, String message) {
        this.currentProcessingStage = stage;
        this.progressPercentage = Math.min(100, Math.max(0, percentage));
        this.progressMessage = message;
        this.processingUpdatedAt = Instant.now();
        if (this.processingStartedAt == null) {
            this.processingStartedAt = Instant.now();
        }
        if (percentage >= 100) {
            this.processingCompletedAt = Instant.now();
        }
    }

    public int getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(int progressPercentage) { this.progressPercentage = progressPercentage; }

    public String getCurrentProcessingStage() { return currentProcessingStage; }
    public void setCurrentProcessingStage(String currentProcessingStage) { this.currentProcessingStage = currentProcessingStage; }

    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public void setProcessingStartedAt(Instant processingStartedAt) { this.processingStartedAt = processingStartedAt; }

    public Instant getProcessingUpdatedAt() { return processingUpdatedAt; }
    public void setProcessingUpdatedAt(Instant processingUpdatedAt) { this.processingUpdatedAt = processingUpdatedAt; }

    public Instant getProcessingCompletedAt() { return processingCompletedAt; }
    public void setProcessingCompletedAt(Instant processingCompletedAt) { this.processingCompletedAt = processingCompletedAt; }

    public String getProgressMessage() { return progressMessage; }
    public void setProgressMessage(String progressMessage) { this.progressMessage = progressMessage; }
}
