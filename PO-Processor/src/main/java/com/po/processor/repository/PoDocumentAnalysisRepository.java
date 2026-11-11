package com.po.processor.repository;

import com.po.processor.entity.PoDocumentAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PoDocumentAnalysis records.
 */
@Repository
public interface PoDocumentAnalysisRepository extends JpaRepository<PoDocumentAnalysis, UUID> {

    List<PoDocumentAnalysis> findByProcessingJobId(UUID processingJobId);

    Optional<PoDocumentAnalysis> findByProcessingJobAttachmentId(UUID attachmentId);
}
