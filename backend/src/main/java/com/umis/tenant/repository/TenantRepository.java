package com.umis.tenant.repository;

import com.umis.tenant.model.Tenant;
import com.umis.tenant.model.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Tenant entities.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByCode(String code);

    boolean existsByCode(String code);

    Page<Tenant> findAllByStatus(TenantStatus status, Pageable pageable);

    long countByStatus(TenantStatus status);
}
