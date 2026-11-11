package com.po.processor.repository;

import com.po.processor.entity.PoExtractionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for PoExtractionRun telemetry records.
 */
@Repository
public interface PoExtractionRunRepository extends JpaRepository<PoExtractionRun, UUID> {

    List<PoExtractionRun> findByProcessingJobIdOrderByCreatedAtDesc(UUID processingJobId);
}
