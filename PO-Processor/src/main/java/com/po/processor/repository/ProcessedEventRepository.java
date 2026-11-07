package com.po.processor.repository;

import com.po.processor.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for idempotency tracking of processed events.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);

    Optional<ProcessedEvent> findByEventIdAndConsumerName(UUID eventId, String consumerName);
}
