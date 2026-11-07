package com.po.processor.repository;

import com.po.processor.entity.ProcessingJob;
import com.po.processor.entity.JobProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PO processing jobs.
 */
@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    Optional<ProcessingJob> findByTenantIdAndUmisEmailId(UUID tenantId, UUID umisEmailId);

    Optional<ProcessingJob> findByEventId(UUID eventId);

    Optional<ProcessingJob> findByUmisEmailId(UUID umisEmailId);

    Page<ProcessingJob> findByTenantId(UUID tenantId, Pageable pageable);

    Page<ProcessingJob> findByProcessingStatus(JobProcessingStatus status, Pageable pageable);
}
