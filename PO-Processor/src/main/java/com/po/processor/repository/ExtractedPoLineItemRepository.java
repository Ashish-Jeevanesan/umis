package com.po.processor.repository;

import com.po.processor.entity.ExtractedPoLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for ExtractedPoLineItem records.
 */
@Repository
public interface ExtractedPoLineItemRepository extends JpaRepository<ExtractedPoLineItem, UUID> {

    List<ExtractedPoLineItem> findByPurchaseOrderIdOrderByLineNumberAsc(UUID purchaseOrderId);

    void deleteByPurchaseOrderId(UUID purchaseOrderId);
}
