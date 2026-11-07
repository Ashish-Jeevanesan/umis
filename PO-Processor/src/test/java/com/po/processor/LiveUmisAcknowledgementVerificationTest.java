package com.po.processor;

import com.po.processor.dto.UmisAcknowledgementRequest;
import com.po.processor.dto.UmisAcknowledgementResponse;
import com.po.processor.dto.UmisProcessingStatus;
import com.po.processor.exception.EventProcessingException;
import com.po.processor.service.UmisClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("Live UMIS Acknowledgement Client Verification Test")
public class LiveUmisAcknowledgementVerificationTest {

    @BeforeAll
    static void initEnv() {
        PoProcessorApplication.loadDotEnvIfExists();
        System.setProperty("UMIS_AUTH_EMAIL", "admin@umis.local");
        System.setProperty("UMIS_AUTH_PASSWORD", "Admin@123");
    }

    @Autowired
    private UmisClient umisClient;

    @Test
    @DisplayName("Verify real authentication and acknowledgement to live UMIS service")
    void testLiveUmisAcknowledgement() {
        // Real email ID from UMIS database
        UUID realEmailId = UUID.fromString("fcbce045-950c-42e6-a315-59a7e806f948");

        UmisAcknowledgementRequest ackRequest = new UmisAcknowledgementRequest(
                "PO_PROCESSING",
                UmisProcessingStatus.PROCESSING,
                Instant.now(),
                "JOB-" + UUID.randomUUID(),
                "Live acknowledgement verification test from PO Processor",
                "KEY-" + UUID.randomUUID()
        );

        UmisAcknowledgementResponse response = umisClient.acknowledge(realEmailId, ackRequest);

        assertNotNull(response, "UMIS response must not be null");
        assertTrue(response.success(), "UMIS acknowledgement must succeed");
        assertNotNull(response.data());
        assertEquals("PO_PROCESSING", response.data().consumerCode());
        assertEquals(realEmailId, response.data().emailId());
    }

    @Test
    @DisplayName("Verify that invalid email ID is correctly rejected with error")
    void testInvalidEmailAcknowledgementRejection() {
        UUID nonExistentEmailId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        UmisAcknowledgementRequest ackRequest = new UmisAcknowledgementRequest(
                "PO_PROCESSING",
                UmisProcessingStatus.PROCESSING,
                Instant.now(),
                "JOB-ERR",
                "Should fail",
                "KEY-ERR"
        );

        assertThrows(EventProcessingException.class, () -> umisClient.acknowledge(nonExistentEmailId, ackRequest));
    }
}
