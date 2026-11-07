package com.umis.email.repository;

import com.umis.email.model.EmailRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for EmailRecipient entities.
 */
@Repository
public interface EmailRecipientRepository extends JpaRepository<EmailRecipient, UUID> {

    List<EmailRecipient> findByEmailId(UUID emailId);
}
