package com.po.processor.repository;

import com.po.processor.entity.ProcessingJobAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for PO processing job attachments.
 */
@Repository
public interface ProcessingJobAttachmentRepository extends JpaRepository<ProcessingJobAttachment, UUID> {

    List<ProcessingJobAttachment> findByProcessingJobId(UUID processingJobId);
}
