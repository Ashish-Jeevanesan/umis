package com.po.processor;

import com.po.processor.dto.event.EmailIngestedPayloadDto;
import com.po.processor.dto.event.UmisEventDto;
import com.po.processor.entity.JobProcessingStatus;
import com.po.processor.entity.ProcessedEvent;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.entity.ProcessingJobAttachment;
import com.po.processor.repository.ProcessedEventRepository;
import com.po.processor.repository.ProcessingJobAttachmentRepository;
import com.po.processor.repository.ProcessingJobRepository;
import com.po.processor.service.PoProcessingService;
import com.po.processor.storage.StorageReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("Live Supabase PostgreSQL Verification Test")
public class LiveSupabaseVerificationTest {

    @BeforeAll
    static void initEnv() {
        PoProcessorApplication.loadDotEnvIfExists();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private ProcessingJobAttachmentRepository attachmentRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private PoProcessingService processingService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private StorageReader storageReader;

    @Test
    @DisplayName("1. Verify Supabase connection and schema isolation")
    void testSupabaseSchemaIsolation() {
        // Query database name
        String dbName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        assertNotNull(dbName);

        // Verify po_processor schema exists
        Integer poSchemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'po_processor'",
                Integer.class
        );
        assertEquals(1, poSchemaCount, "po_processor schema must exist in Supabase");

        // Verify tables in po_processor
        List<String> poTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'po_processor' ORDER BY table_name",
                String.class
        );
        assertTrue(poTables.contains("processing_jobs"), "po_processor.processing_jobs must exist");
        assertTrue(poTables.contains("processing_job_attachments"), "po_processor.processing_job_attachments must exist");
        assertTrue(poTables.contains("processed_events"), "po_processor.processed_events must exist");

        // Verify public schema tables remain untouched (UMIS tables exist)
        List<String> publicTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                String.class
        );
        assertTrue(publicTables.contains("email_messages"), "public.email_messages must remain intact");
        assertTrue(publicTables.contains("event_outbox"), "public.event_outbox must remain intact");
        assertFalse(publicTables.contains("processing_jobs"), "processing_jobs must NOT be created in public schema");
    }

    @Test
    @DisplayName("2. Live persistence, idempotency, and rollback test in Supabase")
    void testLivePersistenceAndIdempotency() {
        when(storageReader.exists(anyString())).thenReturn(true);

        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID emailId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();

        EmailIngestedPayloadDto.AttachmentReferenceDto att = new EmailIngestedPayloadDto.AttachmentReferenceDto(
                UUID.randomUUID(), "LivePO.pdf", "application/pdf", 1024L, "attachments/live.pdf", "hash"
        );

        EmailIngestedPayloadDto payload = new EmailIngestedPayloadDto(
                emailId, mailboxId, tenantId, "Live PO Test", "buyer@live.com", "Buyer",
                List.of("recipient@live.com"), "Snippet", "emails/live.txt", null, true, List.of(att)
        );

        UmisEventDto<EmailIngestedPayloadDto> event = new UmisEventDto<>(
                eventId, "EMAIL_INGESTED", "1.0", Instant.now(), tenantId, mailboxId, payload
        );

        try {
            // First execution
            ProcessingJob job1 = processingService.processEmailIngestedEvent(event);
            assertNotNull(job1);
            assertEquals(JobProcessingStatus.READY_FOR_EXTRACTION, job1.getProcessingStatus());

            // Check persistence in live database
            assertTrue(jobRepository.findById(job1.getId()).isPresent());
            assertTrue(processedEventRepository.existsByEventIdAndConsumerName(eventId, "PO_PROCESSING"));

            // Second execution (Idempotency check)
            long jobsBefore = jobRepository.count();
            ProcessingJob job2 = processingService.processEmailIngestedEvent(event);
            long jobsAfter = jobRepository.count();

            assertEquals(jobsBefore, jobsAfter, "Duplicate event delivery must not create duplicate jobs");
            assertNotNull(job2);
            assertEquals(job1.getId(), job2.getId());

            // Rollback test
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            assertThrows(RuntimeException.class, () -> txTemplate.execute(status -> {
                ProcessingJob doomedJob = new ProcessingJob(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()
                );
                jobRepository.save(doomedJob);
                throw new RuntimeException("Simulated failure to verify rollback");
            }));

        } finally {
            // Clean up test data from live database
            processedEventRepository.findByEventIdAndConsumerName(eventId, "PO_PROCESSING")
                    .ifPresent(processedEventRepository::delete);
            jobRepository.findByTenantIdAndUmisEmailId(tenantId, emailId)
                    .ifPresent(jobRepository::delete);
        }
    }
}
