package com.po.processor.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for PO Processor consumer, defining queues, bindings, and DLQ.
 */
@Configuration
public class RabbitMqConfig {

    private final String exchangeName;
    private final String queueName;
    private final String routingKey;
    private final String dlxExchangeName;
    private final String dlqQueueName;
    private final String dlqRoutingKey;
    private final int prefetchCount;

    public RabbitMqConfig(
            @Value("${po.rabbitmq.exchange:umis.events}") String exchangeName,
            @Value("${po.rabbitmq.queue:po-processor.queue.email-ingested}") String queueName,
            @Value("${po.rabbitmq.routing-key:umis.events.email.ingested}") String routingKey,
            @Value("${po.rabbitmq.dlx-exchange:umis.dlx}") String dlxExchangeName,
            @Value("${po.rabbitmq.dlq-queue:po-processor.queue.dlq}") String dlqQueueName,
            @Value("${po.rabbitmq.dlq-routing-key:po-processor.dlq}") String dlqRoutingKey,
            @Value("${po.rabbitmq.prefetch-count:5}") int prefetchCount
    ) {
        this.exchangeName = exchangeName;
        this.queueName = queueName;
        this.routingKey = routingKey;
        this.dlxExchangeName = dlxExchangeName;
        this.dlqQueueName = dlqQueueName;
        this.dlqRoutingKey = dlqRoutingKey;
        this.prefetchCount = prefetchCount;
    }

    @Bean
    public TopicExchange poUmisEventExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange poDeadLetterExchange() {
        return new DirectExchange(dlxExchangeName, true, false);
    }

    @Bean
    public Queue poDeadLetterQueue() {
        return QueueBuilder.durable(dlqQueueName).build();
    }

    @Bean
    public Binding poDeadLetterBinding() {
        return BindingBuilder.bind(poDeadLetterQueue())
                .to(poDeadLetterExchange())
                .with(dlqRoutingKey);
    }

    @Bean
    public Queue poEmailIngestedQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", dlxExchangeName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding poEmailIngestedBinding() {
        return BindingBuilder.bind(poEmailIngestedQueue())
                .to(poUmisEventExchange())
                .with(routingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(prefetchCount);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
