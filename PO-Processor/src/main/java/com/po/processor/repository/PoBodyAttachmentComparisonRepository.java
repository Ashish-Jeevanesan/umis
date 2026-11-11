package com.po.processor.repository;

import com.po.processor.entity.PoBodyAttachmentComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PoBodyAttachmentComparison records.
 */
@Repository
public interface PoBodyAttachmentComparisonRepository extends JpaRepository<PoBodyAttachmentComparison, UUID> {

    Optional<PoBodyAttachmentComparison> findFirstByPurchaseOrderIdOrderByCreatedAtDesc(UUID purchaseOrderId);

    default Optional<PoBodyAttachmentComparison> findByPurchaseOrderId(UUID purchaseOrderId) {
        return findFirstByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrderId);
    }

    Optional<PoBodyAttachmentComparison> findByProcessingJobId(UUID processingJobId);
}
