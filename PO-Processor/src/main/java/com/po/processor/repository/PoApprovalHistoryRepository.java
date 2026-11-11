package com.po.processor.repository;

import com.po.processor.entity.PoApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for PoApprovalHistory workflow audit logs.
 */
@Repository
public interface PoApprovalHistoryRepository extends JpaRepository<PoApprovalHistory, UUID> {

    List<PoApprovalHistory> findByPurchaseOrderIdOrderByActionAtAsc(UUID purchaseOrderId);
}
