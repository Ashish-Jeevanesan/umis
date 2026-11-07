package com.umis.email.repository;

import com.umis.email.model.EmailMessage;
import com.umis.email.model.IngestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for EmailMessage entities.
 */
@Repository
public interface EmailMessageRepository extends JpaRepository<EmailMessage, UUID> {

    boolean existsByMailboxIdAndProviderMessageId(UUID mailboxId, String providerMessageId);

    Optional<EmailMessage> findByMailboxIdAndProviderMessageId(UUID mailboxId, String providerMessageId);

    Page<EmailMessage> findByTenantId(UUID tenantId, Pageable pageable);

    Page<EmailMessage> findByTenantIdAndMailboxId(UUID tenantId, UUID mailboxId, Pageable pageable);

    Page<EmailMessage> findByTenantIdAndIngestionStatus(UUID tenantId, IngestionStatus status, Pageable pageable);

    Page<EmailMessage> findByMailboxId(UUID mailboxId, Pageable pageable);

    Page<EmailMessage> findByIngestionStatus(IngestionStatus status, Pageable pageable);

    Optional<EmailMessage> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndIngestionStatus(UUID tenantId, IngestionStatus status);

    long countByIngestionStatus(IngestionStatus status);

    @Query("SELECT COUNT(e) FROM EmailMessage e WHERE e.tenantId = :tenantId AND e.receivedAt >= :since")
    long countRecentByTenantId(@Param("tenantId") UUID tenantId, @Param("since") Instant since);
}
