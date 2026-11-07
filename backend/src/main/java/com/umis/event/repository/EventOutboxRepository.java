package com.umis.event.repository;

import com.umis.event.model.EventOutbox;
import com.umis.event.model.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for EventOutbox entities.
 */
@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutbox, UUID> {

    List<EventOutbox> findByStatusOrderByOccurredAtAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    long countByTenantIdAndStatus(UUID tenantId, OutboxStatus status);
}
