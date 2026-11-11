package com.po.processor.repository;

import com.po.processor.entity.PoValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for PoValidationResult rule execution records.
 */
@Repository
public interface PoValidationResultRepository extends JpaRepository<PoValidationResult, UUID> {

    List<PoValidationResult> findByPurchaseOrderId(UUID purchaseOrderId);

    void deleteByPurchaseOrderId(UUID purchaseOrderId);

    long countByPurchaseOrderIdAndPassedFalseAndSeverity(UUID purchaseOrderId, String severity);
}
