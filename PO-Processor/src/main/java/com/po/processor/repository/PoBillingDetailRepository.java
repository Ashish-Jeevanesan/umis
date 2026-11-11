package com.po.processor.repository;

import com.po.processor.entity.PoBillingDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PoBillingDetail records.
 */
@Repository
public interface PoBillingDetailRepository extends JpaRepository<PoBillingDetail, UUID> {

    Optional<PoBillingDetail> findFirstByPurchaseOrderIdOrderByCreatedAtDesc(UUID purchaseOrderId);

    default Optional<PoBillingDetail> findByPurchaseOrderId(UUID purchaseOrderId) {
        return findFirstByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrderId);
    }

    void deleteByPurchaseOrderId(UUID purchaseOrderId);
}
