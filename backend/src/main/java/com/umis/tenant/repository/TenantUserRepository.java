package com.umis.tenant.repository;

import com.umis.tenant.model.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for TenantUser relationship entities.
 */
@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    @Query("SELECT tu FROM TenantUser tu JOIN FETCH tu.tenant WHERE tu.user.id = :userId")
    List<TenantUser> findByUserIdWithTenant(@Param("userId") UUID userId);

    Optional<TenantUser> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);
}
