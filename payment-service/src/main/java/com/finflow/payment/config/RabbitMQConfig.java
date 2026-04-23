package com.finflow.payment.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the Payment Choreography Saga.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>transaction-service publishes PaymentInitiated to payment.events exchange with routing key
 *       'payment.initiated'
 *   <li>This service consumes from payment.service.initiated queue
 *   <li>After processing, publishes PaymentCompleted or PaymentFailed back to payment.events exchange
 *   <li>transaction-service and notification-service consume these replies
 * </ol>
 *
 * <p>Dead letter queue handles messages that fail after all retry attempts.
 */
@Slf4j
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String PAYMENT_INITIATED_ROUTING_KEY = "payment.initiated";
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "payment.failed";
    public static final String PAYMENT_INITIATED_QUEUE = "payment.service.initiated";
    public static final String NOTIFY_COMMANDS_EXCHANGE = "notify.commands";
    public static final String PAYMENT_DLX_EXCHANGE = "payment.dlx";
    public static final String PAYMENT_DLQ_QUEUE = "payment.dead-letter";

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange notifyCommandsExchange() {
        return new DirectExchange(NOTIFY_COMMANDS_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange paymentDlxExchange() {
        return new FanoutExchange(PAYMENT_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentInitiatedQueue() {
        return QueueBuilder.durable(PAYMENT_INITIATED_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_DLX_EXCHANGE)
                .withArgument("x-message-ttl", 60_000)
                .build();
    }

    @Bean
    public Queue paymentDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_DLQ_QUEUE).build();
    }

    @Bean
    public Binding paymentInitiatedBinding(
            @Qualifier("paymentInitiatedQueue") Queue paymentInitiatedQueue,
            TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(paymentInitiatedQueue)
                .to(paymentEventsExchange)
                .with(PAYMENT_INITIATED_ROUTING_KEY);
    }

    /**
     * Binds the DLQ to the fanout DLX. For fanout exchanges RabbitMQ ignores the routing key; {@code
     * "#"} is the conventional match-all pattern from requirements.
     */
    @Bean
    public Binding deadLetterBinding(
            @Qualifier("paymentDeadLetterQueue") Queue paymentDeadLetterQueue,
            FanoutExchange paymentDlxExchange) {
        return new Binding(
                paymentDeadLetterQueue.getName(),
                Binding.DestinationType.QUEUE,
                paymentDlxExchange.getName(),
                "#",
                null);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        template.setMandatory(true);
        template.setConfirmCallback(
                (correlationData, ack, cause) -> {
                    if (!ack) {
                        log.warn("Message not confirmed: {}", cause);
                    }
                });
        template.setReturnsCallback(
                returned -> log.warn("Message returned: {}", returned.getMessage()));
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        return factory;
    }
}
