package com.po.processor.service;

import com.po.processor.dto.event.EmailIngestedPayloadDto;
import com.po.processor.dto.event.UmisEventDto;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.repository.ProcessedEventRepository;
import com.po.processor.repository.ProcessingJobAttachmentRepository;
import com.po.processor.repository.ProcessingJobRepository;
import com.po.processor.storage.StorageReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PO Processing Database Integration Tests")
class PoProcessingIntegrationTest {

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private ProcessingJobAttachmentRepository attachmentRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private PoProcessingService processingService;

    @MockBean
    private StorageReader storageReader;

    @MockBean
    private UmisClient umisClient;

    @BeforeEach
    void setUp() {
        when(umisClient.getConsumerId()).thenReturn("PO_PROCESSING");
        jobRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Should persist ProcessingJob, Attachments, and ProcessedEvent in database")
    @Transactional
    void testEndToEndPersistence() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID emailId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();
        UUID attId = UUID.randomUUID();

        when(storageReader.exists(any())).thenReturn(true);

        EmailIngestedPayloadDto.AttachmentReferenceDto attachmentRef = new EmailIngestedPayloadDto.AttachmentReferenceDto(
                attId, "PO_1001.pdf", "application/pdf", 5000L, "attachments/po.pdf", "hash456"
        );

        EmailIngestedPayloadDto payload = new EmailIngestedPayloadDto(
                emailId, mailboxId, tenantId, "PO Confirmation", "supplier@med.com", "Med Supplier",
                List.of("hospital@test.com"), "PO 1001", "emails/body.txt", null, true, List.of(attachmentRef)
        );

        UmisEventDto<EmailIngestedPayloadDto> event = new UmisEventDto<>(
                eventId, "EMAIL_INGESTED", "1.0", Instant.now(), tenantId, mailboxId, payload
        );

        ProcessingJob job = processingService.processEmailIngestedEvent(event);

        assertNotNull(job);
        assertNotNull(job.getId());
        assertEquals(JobProcessingStatus.QUEUED, job.getProcessingStatus());

        // Verify in repository
        assertTrue(jobRepository.findById(job.getId()).isPresent());
        assertTrue(processedEventRepository.existsByEventIdAndConsumerName(eventId, "PO_PROCESSING"));

        List<ProcessingJob> foundJobs = jobRepository.findAll();
        assertEquals(1, foundJobs.size());
        assertEquals(emailId, foundJobs.get(0).getUmisEmailId());

        // Verify attachments
        assertEquals(1, attachmentRepository.findByProcessingJobId(job.getId()).size());
    }
}
