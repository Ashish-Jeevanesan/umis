package com.po.processor.repository;

import com.po.processor.entity.PoDuplicateDetection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PoDuplicateDetection records.
 */
@Repository
public interface PoDuplicateDetectionRepository extends JpaRepository<PoDuplicateDetection, UUID> {

    Optional<PoDuplicateDetection> findFirstByProcessingJobIdOrderByCreatedAtDesc(UUID processingJobId);

    default Optional<PoDuplicateDetection> findByProcessingJobId(UUID processingJobId) {
        return findFirstByProcessingJobIdOrderByCreatedAtDesc(processingJobId);
    }

    List<PoDuplicateDetection> findByOriginalPurchaseOrderId(UUID originalPoId);
}
