package com.po.processor.dto;

import com.po.processor.entity.ProcessingJob;
import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object carrying live percentage-based progress and stage tracking for a processing job.
 */
public record JobProgressDto(
        UUID jobId,
        String status,
        String stage,
        int progressPercentage,
        String message,
        int attempt,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt,
        String lastError
) {
    public static JobProgressDto from(ProcessingJob job) {
        if (job == null) return null;
        var progress = job.getProgress();
        int percentage = progress != null ? progress.getProgressPercentage() : 0;
        String stage = progress != null ? progress.getCurrentProcessingStage() : "EMAIL_RECEIVED";
        String message = progress != null ? progress.getProgressMessage() : "Email received";
        Instant started = progress != null ? progress.getProcessingStartedAt() : job.getStartedAt();
        Instant updated = progress != null ? progress.getProcessingUpdatedAt() : job.getUpdatedAt();
        Instant completed = progress != null ? progress.getProcessingCompletedAt() : job.getCompletedAt();
        int attempts = job.getWorkerLease() != null ? job.getWorkerLease().getRetryCount() + 1 : 1;

        return new JobProgressDto(
                job.getId(),
                job.getProcessingStatus().name(),
                stage,
                percentage,
                message,
                attempts,
                started,
                updated,
                completed,
                job.getLastError()
        );
    }
}
