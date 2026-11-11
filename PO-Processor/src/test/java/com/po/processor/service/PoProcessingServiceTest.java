package com.po.processor.service;

import com.po.processor.dto.UmisAcknowledgementRequest;
import com.po.processor.dto.UmisProcessingStatus;
import com.po.processor.dto.event.EmailIngestedPayloadDto;
import com.po.processor.dto.event.UmisEventDto;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.ProcessedEvent;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.exception.EventProcessingException;
import com.po.processor.repository.ProcessedEventRepository;
import com.po.processor.repository.ProcessingJobRepository;
import com.po.processor.storage.StorageReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PO Processing Service Tests")
class PoProcessingServiceTest {

    @Mock
    private ProcessingJobRepository jobRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StorageReader storageReader;

    @Mock
    private UmisClient umisClient;

    private PoProcessingService processingService;

    @BeforeEach
    void setUp() {
        lenient().when(umisClient.getConsumerId()).thenReturn("PO_PROCESSING");
        processingService = new PoProcessingService(jobRepository, processedEventRepository, storageReader, umisClient);
    }

    @Test
    @DisplayName("Should process valid EMAIL_INGESTED event and create job")
    void testProcessValidEvent() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID emailId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();

        EmailIngestedPayloadDto.AttachmentReferenceDto att = new EmailIngestedPayloadDto.AttachmentReferenceDto(
                UUID.randomUUID(), "PO-1001.pdf", "application/pdf", 1024L, "attachments/po.pdf", "hash123"
        );

        EmailIngestedPayloadDto payload = new EmailIngestedPayloadDto(
                emailId, mailboxId, tenantId, "PO-1001 Order", "sender@test.com", "Test Sender",
                List.of("recipient@test.com"), "Snippet", "emails/body.txt", null, true, List.of(att)
        );

        UmisEventDto<EmailIngestedPayloadDto> event = new UmisEventDto<>(
                eventId, "EMAIL_INGESTED", "1.0", Instant.now(), tenantId, mailboxId, payload
        );

        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, "PO_PROCESSING")).thenReturn(false);
        when(jobRepository.findByTenantIdAndUmisEmailId(tenantId, emailId)).thenReturn(Optional.empty());
        when(storageReader.exists("emails/body.txt")).thenReturn(true);
        when(storageReader.exists("attachments/po.pdf")).thenReturn(true);
        when(jobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessingJob resultJob = processingService.processEmailIngestedEvent(event);

        assertNotNull(resultJob);
        assertEquals(JobProcessingStatus.QUEUED, resultJob.getProcessingStatus());
        assertEquals(1, resultJob.getAttachments().size());
        assertEquals(JobProcessingStatus.READY_FOR_EXTRACTION, resultJob.getAttachments().get(0).getProcessingStatus());

        verify(jobRepository).save(any(ProcessingJob.class));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(umisClient).acknowledge(eq(emailId), any());
    }

    @Test
    @DisplayName("Should skip processing for duplicate event delivery (Idempotency)")
    void testDuplicateEventDelivery() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID emailId = UUID.randomUUID();

        EmailIngestedPayloadDto payload = new EmailIngestedPayloadDto(
                emailId, UUID.randomUUID(), tenantId, "PO Order", "sender@test.com", "Sender",
                List.of(), "Snippet", null, null, false, List.of()
        );
        UmisEventDto<EmailIngestedPayloadDto> event = new UmisEventDto<>(
                eventId, "EMAIL_INGESTED", "1.0", Instant.now(), tenantId, UUID.randomUUID(), payload
        );

        ProcessingJob existingJob = new ProcessingJob(tenantId, emailId, UUID.randomUUID(), eventId, Instant.now());
        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, "PO_PROCESSING")).thenReturn(true);
        when(jobRepository.findByEventId(eventId)).thenReturn(Optional.of(existingJob));

        ProcessingJob resultJob = processingService.processEmailIngestedEvent(event);

        assertNotNull(resultJob);
        assertEquals(existingJob, resultJob);
        verify(jobRepository, never()).save(any(ProcessingJob.class));
        verify(umisClient, never()).acknowledge(any(), any());
    }

    @Test
    @DisplayName("Should flag attachment as FAILED if missing in storage but continue job")
    void testMissingAttachmentInStorage() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID emailId = UUID.randomUUID();

        EmailIngestedPayloadDto.AttachmentReferenceDto att = new EmailIngestedPayloadDto.AttachmentReferenceDto(
                UUID.randomUUID(), "PO.pdf", "application/pdf", 1024L, "attachments/missing.pdf", "hash"
        );

        EmailIngestedPayloadDto payload = new EmailIngestedPayloadDto(
                emailId, UUID.randomUUID(), tenantId, "Purchase Order PO-5001", "s@test.com", "Sender",
                List.of(), "Snippet", null, null, true, List.of(att)
        );
        UmisEventDto<EmailIngestedPayloadDto> event = new UmisEventDto<>(
                eventId, "EMAIL_INGESTED", "1.0", Instant.now(), tenantId, UUID.randomUUID(), payload
        );

        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, "PO_PROCESSING")).thenReturn(false);
        when(jobRepository.findByTenantIdAndUmisEmailId(tenantId, emailId)).thenReturn(Optional.empty());
        when(storageReader.exists("attachments/missing.pdf")).thenReturn(false);
        when(jobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessingJob resultJob = processingService.processEmailIngestedEvent(event);

        assertNotNull(resultJob);
        assertEquals(JobProcessingStatus.QUEUED, resultJob.getProcessingStatus());
        assertEquals(JobProcessingStatus.FAILED, resultJob.getAttachments().get(0).getProcessingStatus());
    }

    @Test
    @DisplayName("Should throw EventProcessingException on invalid event payload")
    void testInvalidEventPayload() {
        assertThrows(EventProcessingException.class, () -> processingService.processEmailIngestedEvent(null));

        UmisEventDto<EmailIngestedPayloadDto> nullIdEvent = new UmisEventDto<>(
                null, "EMAIL_INGESTED", "1.0", Instant.now(), UUID.randomUUID(), UUID.randomUUID(), null
        );
        assertThrows(EventProcessingException.class, () -> processingService.processEmailIngestedEvent(nullIdEvent));
    }

    @Test
    @DisplayName("Should skip extraction and acknowledge IGNORED for an email with no PO indicators")
    void testNonPurchaseOrderEmailIsSkipped() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID emailId = UUID.randomUUID();

        EmailIngestedPayloadDto.AttachmentReferenceDto resume = new EmailIngestedPayloadDto.AttachmentReferenceDto(
                UUID.randomUUID(), "Resume_AshishJeevanesan.pdf", "application/pdf", 2048L,
                "attachments/resume.pdf", "hash456"
        );

        EmailIngestedPayloadDto payload = new EmailIngestedPayloadDto(
                emailId, UUID.randomUUID(), tenantId,
                "Ashish Jeevanesan - Application for Opportunities at Marek Systems",
                "nanubony@gmail.com", "Ashish J",
                List.of("recipient@test.com"), "Please find my resume attached.",
                "emails/body.txt", null, true, List.of(resume)
        );

        UmisEventDto<EmailIngestedPayloadDto> event = new UmisEventDto<>(
                eventId, "EMAIL_INGESTED", "1.0", Instant.now(), tenantId, UUID.randomUUID(), payload
        );

        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, "PO_PROCESSING")).thenReturn(false);
        when(jobRepository.findByTenantIdAndUmisEmailId(tenantId, emailId)).thenReturn(Optional.empty());
        when(jobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessingJob resultJob = processingService.processEmailIngestedEvent(event);

        assertNotNull(resultJob);
        assertEquals(JobProcessingStatus.SKIPPED, resultJob.getProcessingStatus());
        assertEquals(0, resultJob.getAttachments().size());

        verify(storageReader, never()).exists(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));

        ArgumentCaptor<UmisAcknowledgementRequest> ackCaptor = ArgumentCaptor.forClass(UmisAcknowledgementRequest.class);
        verify(umisClient).acknowledge(eq(emailId), ackCaptor.capture());
        assertEquals(UmisProcessingStatus.IGNORED, ackCaptor.getValue().status());
    }

    @Test
    @DisplayName("Should still process a PO-named attachment even with a generic subject")
    void testPurchaseOrderAttachmentNameWithGenericSubjectIsProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID emailId = UUID.randomUUID();

        EmailIngestedPayloadDto.AttachmentReferenceDto att = new EmailIngestedPayloadDto.AttachmentReferenceDto(
                UUID.randomUUID(), "PO-1993.pdf", "application/pdf", 1024L, "attachments/po.pdf", "hash789"
        );

        EmailIngestedPayloadDto payload = new EmailIngestedPayloadDto(
                emailId, UUID.randomUUID(), tenantId, "Please review", "sender@test.com", "Vendor",
                List.of(), "See attached", "emails/body.txt", null, true, List.of(att)
        );

        UmisEventDto<EmailIngestedPayloadDto> event = new UmisEventDto<>(
                eventId, "EMAIL_INGESTED", "1.0", Instant.now(), tenantId, UUID.randomUUID(), payload
        );

        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, "PO_PROCESSING")).thenReturn(false);
        when(jobRepository.findByTenantIdAndUmisEmailId(tenantId, emailId)).thenReturn(Optional.empty());
        when(storageReader.exists("emails/body.txt")).thenReturn(true);
        when(storageReader.exists("attachments/po.pdf")).thenReturn(true);
        when(jobRepository.save(any(ProcessingJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProcessingJob resultJob = processingService.processEmailIngestedEvent(event);

        assertNotNull(resultJob);
        assertEquals(JobProcessingStatus.QUEUED, resultJob.getProcessingStatus());
        assertEquals(1, resultJob.getAttachments().size());
    }
}
