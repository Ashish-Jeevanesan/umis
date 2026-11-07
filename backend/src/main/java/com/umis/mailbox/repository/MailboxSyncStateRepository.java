package com.umis.mailbox.repository;

import com.umis.mailbox.model.MailboxSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for MailboxSyncState entities.
 */
@Repository
public interface MailboxSyncStateRepository extends JpaRepository<MailboxSyncState, UUID> {

    Optional<MailboxSyncState> findByMailboxId(UUID mailboxId);

    @Modifying
    @Query("UPDATE MailboxSyncState s SET s.syncLockToken = :token, s.syncLockAcquiredAt = :now " +
            "WHERE s.mailbox.id = :mailboxId AND (s.syncLockToken IS NULL OR s.syncLockAcquiredAt < :expireBefore)")
    int acquireLock(
            @Param("mailboxId") UUID mailboxId,
            @Param("token") String token,
            @Param("now") Instant now,
            @Param("expireBefore") Instant expireBefore
    );

    @Modifying
    @Query("UPDATE MailboxSyncState s SET s.syncLockToken = NULL, s.syncLockAcquiredAt = NULL " +
            "WHERE s.mailbox.id = :mailboxId AND s.syncLockToken = :token")
    int releaseLock(@Param("mailboxId") UUID mailboxId, @Param("token") String token);
}
