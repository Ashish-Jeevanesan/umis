package com.po.processor.repository;

import com.po.processor.entity.PoShippingDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PoShippingDetail records.
 */
@Repository
public interface PoShippingDetailRepository extends JpaRepository<PoShippingDetail, UUID> {

    Optional<PoShippingDetail> findFirstByPurchaseOrderIdOrderByCreatedAtDesc(UUID purchaseOrderId);

    default Optional<PoShippingDetail> findByPurchaseOrderId(UUID purchaseOrderId) {
        return findFirstByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrderId);
    }

    void deleteByPurchaseOrderId(UUID purchaseOrderId);
}
