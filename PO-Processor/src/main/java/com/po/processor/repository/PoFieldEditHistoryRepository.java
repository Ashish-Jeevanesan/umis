package com.po.processor.repository;

import com.po.processor.entity.PoFieldEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for PoFieldEditHistory human modification audit records.
 */
@Repository
public interface PoFieldEditHistoryRepository extends JpaRepository<PoFieldEditHistory, UUID> {

    List<PoFieldEditHistory> findByPurchaseOrderIdOrderByEditedAtDesc(UUID purchaseOrderId);
}
