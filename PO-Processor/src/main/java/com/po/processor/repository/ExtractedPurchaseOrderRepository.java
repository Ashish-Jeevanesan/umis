package com.po.processor.repository;

import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.PoWorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for canonical ExtractedPurchaseOrder records.
 */
@Repository
public interface ExtractedPurchaseOrderRepository extends JpaRepository<ExtractedPurchaseOrder, UUID>,
        JpaSpecificationExecutor<ExtractedPurchaseOrder> {

    Optional<ExtractedPurchaseOrder> findFirstByProcessingJobIdOrderByCreatedAtDesc(UUID processingJobId);

    Optional<ExtractedPurchaseOrder> findFirstByProcessingJobIdAndTenantIdOrderByCreatedAtDesc(UUID processingJobId, UUID tenantId);

    default Optional<ExtractedPurchaseOrder> findByProcessingJobId(UUID processingJobId) {
        return findFirstByProcessingJobIdOrderByCreatedAtDesc(processingJobId);
    }

    default Optional<ExtractedPurchaseOrder> findByProcessingJobIdAndTenantId(UUID processingJobId, UUID tenantId) {
        return findFirstByProcessingJobIdAndTenantIdOrderByCreatedAtDesc(processingJobId, tenantId);
    }

    Optional<ExtractedPurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ExtractedPurchaseOrder> findByTenantIdAndPoNumber(UUID tenantId, String poNumber);

    List<ExtractedPurchaseOrder> findByTenantIdAndPoNumberAndIsApprovedTrueAndIsSupersededFalse(
            UUID tenantId,
            String poNumber
    );

    Optional<ExtractedPurchaseOrder> findFirstByTenantIdAndPoNumberOrderByRevisionNumberDesc(
            UUID tenantId,
            String poNumber
    );

    List<ExtractedPurchaseOrder> findByTenantIdAndHospitalIdOrderByPoDateDesc(UUID tenantId, UUID hospitalId);

    List<ExtractedPurchaseOrder> findByTenantIdAndWorkflowStatus(UUID tenantId, PoWorkflowStatus workflowStatus);

    /**
     * Interface-based projection for currency aggregates of approved purchase orders.
     */
    interface CurrencySummaryProjection {
        String getCurrency();
        long getApprovedCount();
        BigDecimal getTotalAmount();
    }

    /**
     * Aggregates historical approved totals dynamically grouped by currency for a canonical hospital.
     * Only approved, non-superseded purchase orders are aggregated.
     *
     * @param tenantId Tenant identifier
     * @param hospitalId Canonical hospital identifier
     * @return List of currency summary aggregates
     */
    @Query("""
        SELECT po.currency AS currency,
               COUNT(po) AS approvedCount,
               SUM(po.grandTotal) AS totalAmount
        FROM ExtractedPurchaseOrder po
        WHERE po.tenantId = :tenantId
          AND po.hospital.id = :hospitalId
          AND po.isApproved = true
          AND po.isSuperseded = false
          AND po.workflowStatus = 'APPROVED'
        GROUP BY po.currency
        ORDER BY po.currency ASC
    """)
    List<CurrencySummaryProjection> findApprovedTotalsByCurrency(
            @Param("tenantId") UUID tenantId,
            @Param("hospitalId") UUID hospitalId
    );
}
