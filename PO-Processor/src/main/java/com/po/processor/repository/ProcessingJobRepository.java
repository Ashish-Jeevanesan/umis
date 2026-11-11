package com.po.processor.repository;

import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.ProcessingJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PO processing jobs.
 */
@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID>,
        JpaSpecificationExecutor<ProcessingJob> {

    Optional<ProcessingJob> findByTenantIdAndUmisEmailId(UUID tenantId, UUID umisEmailId);

    Optional<ProcessingJob> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ProcessingJob> findByEventId(UUID eventId);

    Optional<ProcessingJob> findByUmisEmailId(UUID umisEmailId);

    Page<ProcessingJob> findByTenantId(UUID tenantId, Pageable pageable);

    Page<ProcessingJob> findByProcessingStatus(JobProcessingStatus status, Pageable pageable);

    long countByProcessingStatus(JobProcessingStatus status);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndProcessingStatus(UUID tenantId, JobProcessingStatus status);

    /**
     * Finds jobs eligible for worker claiming: QUEUED or expired PROCESSING leases.
     *
     * @param queuedStatus QUEUED status
     * @param processingStatus PROCESSING status
     * @param now Current timestamp
     * @param pageable Limit results
     * @return List of claimable jobs
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT pj FROM ProcessingJob pj
        WHERE (
            (pj.processingStatus = :queuedStatus AND (pj.workerLease.nextRetryAt IS NULL OR pj.workerLease.nextRetryAt <= :now))
            OR (pj.processingStatus = :processingStatus AND pj.workerLease.lockExpiresAt IS NOT NULL AND pj.workerLease.lockExpiresAt < :now)
        )
        ORDER BY pj.receivedAt ASC
    """)
    java.util.List<ProcessingJob> findClaimableJobs(
            @org.springframework.data.repository.query.Param("queuedStatus") JobProcessingStatus queuedStatus,
            @org.springframework.data.repository.query.Param("processingStatus") JobProcessingStatus processingStatus,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now,
            Pageable pageable
    );
}
