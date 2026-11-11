package com.po.processor.repository;

import com.po.processor.entity.PoPatientDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for sensitive PoPatientDetail records.
 */
@Repository
public interface PoPatientDetailRepository extends JpaRepository<PoPatientDetail, UUID> {

    Optional<PoPatientDetail> findFirstByPurchaseOrderIdOrderByCreatedAtDesc(UUID purchaseOrderId);

    default Optional<PoPatientDetail> findByPurchaseOrderId(UUID purchaseOrderId) {
        return findFirstByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrderId);
    }

    void deleteByPurchaseOrderId(UUID purchaseOrderId);
}
