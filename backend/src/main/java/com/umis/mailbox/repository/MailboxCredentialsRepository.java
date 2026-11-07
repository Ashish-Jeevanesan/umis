package com.umis.mailbox.repository;

import com.umis.mailbox.model.MailboxCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for MailboxCredentials entities.
 */
@Repository
public interface MailboxCredentialsRepository extends JpaRepository<MailboxCredentials, UUID> {

    Optional<MailboxCredentials> findByMailboxId(UUID mailboxId);
}
