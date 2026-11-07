package com.umis.mailbox.repository;

import com.umis.mailbox.model.MailboxConnection;
import com.umis.mailbox.model.MailboxProvider;
import com.umis.mailbox.model.MailboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for MailboxConnection entities.
 */
@Repository
public interface MailboxConnectionRepository extends JpaRepository<MailboxConnection, UUID> {

    Page<MailboxConnection> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<MailboxConnection> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndEmailAddress(UUID tenantId, String emailAddress);

    List<MailboxConnection> findAllByStatus(MailboxStatus status);

    long countByTenantId(UUID tenantId);

    long countByStatus(MailboxStatus status);

    long countByTenantIdAndStatus(UUID tenantId, MailboxStatus status);

    List<MailboxConnection> findByProvider(MailboxProvider provider);
}
