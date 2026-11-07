package com.po.processor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.service.PoProcessingService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PO Email Ingested Consumer Tests")
class PoEmailIngestedConsumerTest {

    @Mock
    private PoProcessingService processingService;

    @Mock
    private Channel channel;

    private PoEmailIngestedConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new PoEmailIngestedConsumer(processingService, objectMapper, 3);
    }

    @Test
    @DisplayName("Should acknowledge message upon successful processing")
    void testSuccessfulMessageProcessing() throws IOException {
        String validJson = """
        {
          "eventId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
          "eventType": "EMAIL_INGESTED",
          "eventVersion": "1.0",
          "occurredAt": "2026-11-08T03:30:00Z",
          "tenantId": "11111111-1111-1111-1111-111111111111",
          "mailboxId": "22222222-2222-2222-2222-222222222222",
          "payload": {
            "emailId": "33333333-3333-3333-3333-333333333333",
            "subject": "PO Test",
            "sender": "supplier@test.com",
            "hasAttachments": false,
            "attachments": []
          }
        }
        """;

        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(42L);
        Message message = new Message(validJson.getBytes(StandardCharsets.UTF_8), props);

        when(processingService.processEmailIngestedEvent(any())).thenReturn(new ProcessingJob());

        consumer.onEmailIngestedMessage(message, channel);

        verify(channel).basicAck(42L, false);
    }

    @Test
    @DisplayName("Should dead-letter malformed JSON without requeue")
    void testMalformedJsonDeadLettering() throws IOException {
        String brokenJson = "{ invalid json ";
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(99L);
        Message message = new Message(brokenJson.getBytes(StandardCharsets.UTF_8), props);

        consumer.onEmailIngestedMessage(message, channel);

        verify(channel).basicNack(99L, false, false);
    }

    @Test
    @DisplayName("Should requeue for retry when failure count is below maxRetries")
    void testRequeueOnTransientFailure() throws IOException {
        String validJson = """
        {
          "eventId": "%s",
          "eventType": "EMAIL_INGESTED",
          "payload": { "emailId": "%s" }
        }
        """.formatted(UUID.randomUUID(), UUID.randomUUID());

        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(101L);
        props.setRedelivered(false);
        Message message = new Message(validJson.getBytes(StandardCharsets.UTF_8), props);

        when(processingService.processEmailIngestedEvent(any())).thenThrow(new RuntimeException("Transient DB timeout"));

        consumer.onEmailIngestedMessage(message, channel);

        verify(channel).basicNack(101L, false, true);
    }

    @Test
    @DisplayName("Should dead-letter to DLQ when death count reaches maxRetries")
    void testDeadLetterOnMaxRetries() throws IOException {
        String validJson = """
        {
          "eventId": "%s",
          "eventType": "EMAIL_INGESTED",
          "payload": { "emailId": "%s" }
        }
        """.formatted(UUID.randomUUID(), UUID.randomUUID());

        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(202L);
        props.setHeader("x-death", List.of(Map.of("count", 3L, "queue", "po-processor.queue.email-ingested")));
        Message message = new Message(validJson.getBytes(StandardCharsets.UTF_8), props);

        when(processingService.processEmailIngestedEvent(any())).thenThrow(new RuntimeException("Persistent failure"));

        consumer.onEmailIngestedMessage(message, channel);

        verify(channel).basicNack(202L, false, false);
    }
}
