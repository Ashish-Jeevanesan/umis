package com.umis.event.publisher;

import com.umis.config.UmisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher responsible for transmitting serialized event messages to the RabbitMQ exchange.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final UmisProperties properties;

    public EventPublisher(RabbitTemplate rabbitTemplate, UmisProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(String eventType, String payloadJson) {
        String exchange = properties.getRabbitmq().getExchange();
        String routingKey = properties.getRabbitmq().getRoutingKeyPrefix() + "." + eventType.toLowerCase().replace("_", ".");
        log.debug("Publishing event to exchange {} with routingKey {}: {}", exchange, routingKey, eventType);
        // Explicit, rather than relying on converter/broker defaults: a durable queue only
        // survives a broker restart if the messages in it are also marked persistent.
        MessagePostProcessor persistent = message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        };
        rabbitTemplate.convertAndSend(exchange, routingKey, payloadJson, persistent);
    }
}
