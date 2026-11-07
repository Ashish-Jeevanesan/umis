package com.umis.event.consumer.repository;

import com.umis.event.consumer.model.EmailProcessingStatus;
import com.umis.event.consumer.model.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for EmailProcessingStatus entities.
 */
@Repository
public interface EmailProcessingStatusRepository extends JpaRepository<EmailProcessingStatus, UUID> {

    Optional<EmailProcessingStatus> findByEmailIdAndConsumerCode(UUID emailId, String consumerCode);

    List<EmailProcessingStatus> findByEmailId(UUID emailId);

    long countByStatus(ProcessingStatus status);

    @Query("SELECT COUNT(eps) FROM EmailProcessingStatus eps WHERE eps.email.tenantId = :tenantId AND eps.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") ProcessingStatus status);
}
