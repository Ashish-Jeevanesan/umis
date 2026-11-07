package com.umis.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure configuration for durable exchanges, queues, and dead-letter handling.
 */
@Configuration
public class RabbitMqConfig {

    public static final String DEAD_LETTER_EXCHANGE = "umis.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "umis.dlq";

    private final UmisProperties properties;

    public RabbitMqConfig(UmisProperties properties) {
        this.properties = properties;
    }

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(properties.getRabbitmq().getExchange(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(properties.getRabbitmq().getQueueDlq()).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Queue emailIngestedQueue() {
        return QueueBuilder.durable(properties.getRabbitmq().getQueueEmailIngested())
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding emailIngestedBinding() {
        return BindingBuilder.bind(emailIngestedQueue())
                .to(eventExchange())
                .with(properties.getRabbitmq().getRoutingKeyPrefix() + ".email.ingested");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
