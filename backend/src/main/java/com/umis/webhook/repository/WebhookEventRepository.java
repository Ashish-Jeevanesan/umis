package com.umis.webhook.repository;

import com.umis.webhook.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for WebhookEvent entities.
 */
@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
}
