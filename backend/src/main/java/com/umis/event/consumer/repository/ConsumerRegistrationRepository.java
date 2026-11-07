package com.umis.event.consumer.repository;

import com.umis.event.consumer.model.ConsumerRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for ConsumerRegistration entities.
 */
@Repository
public interface ConsumerRegistrationRepository extends JpaRepository<ConsumerRegistration, UUID> {

    Optional<ConsumerRegistration> findByTenantIdAndConsumerCode(UUID tenantId, String consumerCode);

    Optional<ConsumerRegistration> findByConsumerCodeAndTenantIdIsNull(String consumerCode);

    Page<ConsumerRegistration> findByTenantIdOrTenantIdIsNull(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndConsumerCode(UUID tenantId, String consumerCode);
}
