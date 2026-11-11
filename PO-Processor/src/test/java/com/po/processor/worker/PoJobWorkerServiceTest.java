package com.po.processor.worker;

import com.po.processor.entity.ExtractedPurchaseOrder;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.repository.ExtractedPurchaseOrderRepository;
import com.po.processor.repository.ProcessingJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test verifying that the database-backed worker claims queued jobs and completes extraction.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PoJobWorkerServiceTest {

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private ExtractedPurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PoJobWorkerService workerService;

    @Test
    @DisplayName("Verify worker service claims a queued job and processes it into READY_FOR_REVIEW")
    void testWorkerClaimsAndProcessesJob() {
        UUID tenantId = UUID.randomUUID();
        ProcessingJob job = new ProcessingJob(tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        job.setProcessingStatus(JobProcessingStatus.QUEUED);
        job.setEmailSubject("PO-TEST-ORDER-123 from Mercy Hospital");
        job.setEmailSender("billing@mercy.org");
        ProcessingJob savedJob = jobRepository.save(job);

        // Execute worker claim and process
        workerService.claimAndExecute(savedJob.getId());

        // Verify job state
        ProcessingJob updatedJob = jobRepository.findById(savedJob.getId()).orElseThrow();
        assertEquals(JobProcessingStatus.READY_FOR_REVIEW, updatedJob.getProcessingStatus());
        assertNotNull(updatedJob.getCompletedAt());

        // Verify extracted PO persisted
        Optional<ExtractedPurchaseOrder> poOpt = purchaseOrderRepository.findByProcessingJobId(savedJob.getId());
        assertTrue(poOpt.isPresent());
        ExtractedPurchaseOrder po = poOpt.get();
        assertNotNull(po.getPoNumber());
        assertNotNull(po.getGrandTotal());
    }
}
