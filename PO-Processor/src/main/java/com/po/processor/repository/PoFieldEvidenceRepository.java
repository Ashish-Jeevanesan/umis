package com.po.processor.repository;

import com.po.processor.entity.PoFieldEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for field-level provenance and confidence evidence.
 */
@Repository
public interface PoFieldEvidenceRepository extends JpaRepository<PoFieldEvidence, UUID> {

    List<PoFieldEvidence> findByPurchaseOrderId(UUID purchaseOrderId);

    Optional<PoFieldEvidence> findByPurchaseOrderIdAndFieldName(UUID purchaseOrderId, String fieldName);

    void deleteByPurchaseOrderId(UUID purchaseOrderId);
}
