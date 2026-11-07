package com.umis.email.repository;

import com.umis.email.model.IngestionJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for IngestionJob entities.
 */
@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    Page<IngestionJob> findByTenantId(UUID tenantId, Pageable pageable);

    List<IngestionJob> findTop5ByMailboxIdOrderByStartedAtDesc(UUID mailboxId);

    @Query("SELECT COALESCE(SUM(j.itemsDiscovered), 0) FROM IngestionJob j")
    long sumAllItemsDiscovered();

    @Query("SELECT COALESCE(SUM(j.itemsDiscovered), 0) FROM IngestionJob j WHERE j.tenantId = :tenantId")
    long sumItemsDiscoveredByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT COALESCE(SUM(j.itemsSkipped), 0) FROM IngestionJob j")
    long sumAllItemsSkipped();

    @Query("SELECT COALESCE(SUM(j.itemsSkipped), 0) FROM IngestionJob j WHERE j.tenantId = :tenantId")
    long sumItemsSkippedByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT COALESCE(SUM(j.itemsFailed), 0) FROM IngestionJob j")
    long sumAllItemsFailed();

    @Query("SELECT COALESCE(SUM(j.itemsFailed), 0) FROM IngestionJob j WHERE j.tenantId = :tenantId")
    long sumItemsFailedByTenantId(@Param("tenantId") UUID tenantId);
}
