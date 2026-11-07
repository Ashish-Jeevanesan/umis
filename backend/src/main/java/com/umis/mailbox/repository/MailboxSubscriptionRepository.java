package com.umis.mailbox.repository;

import com.umis.mailbox.model.MailboxSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for MailboxSubscription entities.
 */
@Repository
public interface MailboxSubscriptionRepository extends JpaRepository<MailboxSubscription, UUID> {

    Optional<MailboxSubscription> findBySubscriptionId(String subscriptionId);

    List<MailboxSubscription> findByMailboxId(UUID mailboxId);

    List<MailboxSubscription> findByExpiresAtBeforeAndStatus(Instant threshold, String status);
}
