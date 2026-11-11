package com.po.processor.repository;

import com.po.processor.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for canonical Hospital entities.
 */
@Repository
public interface HospitalRepository extends JpaRepository<Hospital, UUID> {

    Optional<Hospital> findByTenantIdAndNormalizedName(UUID tenantId, String normalizedName);

    Optional<Hospital> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Hospital> findByTenantIdAndHospitalCode(UUID tenantId, String hospitalCode);

    List<Hospital> findByTenantIdOrderByNormalizedNameAsc(UUID tenantId);
}
