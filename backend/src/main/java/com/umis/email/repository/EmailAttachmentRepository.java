package com.umis.email.repository;

import com.umis.email.model.EmailAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for EmailAttachment entities.
 */
@Repository
public interface EmailAttachmentRepository extends JpaRepository<EmailAttachment, UUID> {

    List<EmailAttachment> findByEmailId(UUID emailId);

    Optional<EmailAttachment> findByIdAndEmailId(UUID id, UUID emailId);

    long countByTenantId(UUID tenantId);
}
