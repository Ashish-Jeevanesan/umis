package com.po.processor.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.po.processor.dto.event.EmailIngestedPayloadDto;
import com.po.processor.dto.event.UmisEventDto;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.service.PoProcessingService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ consumer listening for UMIS EMAIL_INGESTED events on a dedicated durable queue.
 */
@Component
public class PoEmailIngestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PoEmailIngestedConsumer.class);

    private final PoProcessingService processingService;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    public PoEmailIngestedConsumer(
            PoProcessingService processingService,
            ObjectMapper objectMapper,
            @Value("${po.rabbitmq.max-retries:3}") int maxRetries
    ) {
        this.processingService = processingService;
        this.objectMapper = objectMapper;
        this.maxRetries = maxRetries;
    }

    @RabbitListener(
            queues = "${po.rabbitmq.queue:po-processor.queue.email-ingested}",
            containerFactory = "rabbitListenerContainerFactory",
            ackMode = "MANUAL"
    )
    public void onEmailIngestedMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageBody = new String(message.getBody(), StandardCharsets.UTF_8);

        UmisEventDto<EmailIngestedPayloadDto> event;
        try {
            String rawJson = messageBody.trim();
            if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
                rawJson = objectMapper.readValue(rawJson, String.class);
            }
            event = objectMapper.readValue(
                    rawJson,
                    new TypeReference<UmisEventDto<EmailIngestedPayloadDto>>() {}
            );
        } catch (Exception parseException) {
            log.error("Fatal JSON parse failure for message tag {}. Dead-lettering immediately: {}",
                    deliveryTag, parseException.getMessage());
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        String eventIdStr = event.eventId() != null ? event.eventId().toString() : "UNKNOWN";
        String emailIdStr = event.payload() != null && event.payload().emailId() != null
                ? event.payload().emailId().toString() : "UNKNOWN";
        String tenantIdStr = event.tenantId() != null ? event.tenantId().toString() : "UNKNOWN";

        log.info("Received event [ID: {}] for email: {} [Tenant: {}]", eventIdStr, emailIdStr, tenantIdStr);

        try {
            ProcessingJob job = processingService.processEmailIngestedEvent(event);
            if (job != null) {
                log.info("Successfully processed PO job {} for email {}", job.getId(), emailIdStr);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception processingException) {
            handleProcessingFailure(message, channel, deliveryTag, eventIdStr, emailIdStr, processingException);
        }
    }

    private void handleProcessingFailure(
            Message message,
            Channel channel,
            long deliveryTag,
            String eventIdStr,
            String emailIdStr,
            Exception processingException
    ) throws IOException {
        Integer deathCount = getDeathCount(message);
        log.warn("Processing failed for event {} (email: {}), death count: {}. Error: {}",
                eventIdStr, emailIdStr, deathCount, processingException.getMessage());

        if (deathCount >= maxRetries) {
            log.error("Max retries ({}) exceeded for event {}. Moving to dead-letter queue.", maxRetries, eventIdStr);
            channel.basicNack(deliveryTag, false, false);
        } else {
            log.info("Requeuing event {} for retry (attempt {})", eventIdStr, deathCount + 1);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private Integer getDeathCount(Message message) {
        Object deathHeader = message.getMessageProperties().getHeaders().get("x-death");
        if (deathHeader instanceof java.util.List<?> deathList && !deathList.isEmpty()) {
            Object firstEntry = deathList.get(0);
            if (firstEntry instanceof java.util.Map<?, ?> deathMap && deathMap.get("count") != null) {
                return ((Number) deathMap.get("count")).intValue();
            }
        }
        return message.getMessageProperties().isRedelivered() ? 1 : 0;
    }
}
