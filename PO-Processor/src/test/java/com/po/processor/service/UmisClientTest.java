package com.po.processor.service;

import com.po.processor.dto.UmisAcknowledgementRequest;
import com.po.processor.dto.UmisAcknowledgementResponse;
import com.po.processor.dto.UmisProcessingStatus;
import com.po.processor.exception.EventProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("UMIS Client Tests")
class UmisClientTest {

    private UmisClient umisClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        umisClient = new UmisClient("http://localhost:8080", "PO_PROCESSING", "", "");
        RestTemplate template = (RestTemplate) ReflectionTestUtils.getField(umisClient, "restTemplate");
        mockServer = MockRestServiceServer.createServer(template);
    }

    @Test
    @DisplayName("Should deliver acknowledgement successfully to UMIS")
    void testSuccessfulAcknowledgement() {
        UUID emailId = UUID.randomUUID();
        String expectedUrl = "http://localhost:8080/api/v1/emails/" + emailId + "/acknowledgements";

        String mockResponseJson = """
        {
          "success": true,
          "message": "Processing outcome recorded",
          "data": {
            "id": "11111111-1111-1111-1111-111111111111",
            "emailId": "%s",
            "consumerCode": "PO_PROCESSING",
            "status": "PROCESSING"
          }
        }
        """.formatted(emailId);

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        UmisAcknowledgementRequest request = new UmisAcknowledgementRequest(
                "PO_PROCESSING", UmisProcessingStatus.PROCESSING, Instant.now(), "REF-1", "Ready", "KEY-1"
        );

        UmisAcknowledgementResponse response = umisClient.acknowledge(emailId, request);

        mockServer.verify();
        assertNotNull(response);
        assertTrue(response.success());
        assertEquals("PO_PROCESSING", response.data().consumerCode());
    }

    @Test
    @DisplayName("Should throw EventProcessingException on UMIS HTTP error")
    void testAcknowledgementHttpError() {
        UUID emailId = UUID.randomUUID();
        String expectedUrl = "http://localhost:8080/api/v1/emails/" + emailId + "/acknowledgements";

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        UmisAcknowledgementRequest request = new UmisAcknowledgementRequest(
                "PO_PROCESSING", UmisProcessingStatus.PROCESSING, Instant.now(), "REF-1", "Ready", "KEY-1"
        );

        assertThrows(EventProcessingException.class, () -> umisClient.acknowledge(emailId, request));
        mockServer.verify();
    }
}
