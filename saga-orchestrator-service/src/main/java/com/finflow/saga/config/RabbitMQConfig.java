package com.finflow.saga.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the Orchestration Saga.
 *
 * <p>Exchange/Queue topology:
 *
 * <p>saga.commands (DirectExchange):
 *
 * <p>routing key 'account.commands' -> account-service queue routing key 'saga.notify.welcome' ->
 * notification-service queue routing key 'saga.account.compensation' -> account-service
 * compensation queue
 *
 * <p>saga.replies (DirectExchange):
 *
 * <p>routing key 'saga.replies' -> saga.replies.queue (all participants reply to same queue,
 * differentiated by stepName)
 *
 * <p>saga.dlx (FanoutExchange):
 *
 * <p>All failed messages from saga.replies.queue go here
 *
 * <p>Concurrency note: prefetchCount=1 ensures the orchestrator processes replies sequentially,
 * preventing two replies from the same saga from creating race conditions.
 */
@Configuration
@Slf4j
public class RabbitMQConfig {

    public static final String SAGA_COMMANDS_EXCHANGE = "saga.commands";
    public static final String SAGA_REPLIES_EXCHANGE = "saga.replies";
    public static final String SAGA_DLX_EXCHANGE = "saga.dlx";
    public static final String SAGA_REPLIES_QUEUE = "saga.replies.queue";
    public static final String SAGA_DLQ_QUEUE = "saga.dead-letter";
    public static final String ACCOUNT_COMMANDS_ROUTING_KEY = "account.commands";
    public static final String NOTIFY_WELCOME_ROUTING_KEY = "saga.notify.welcome";
    public static final String SAGA_REPLIES_ROUTING_KEY = "saga.replies";

    @Bean
    public DirectExchange sagaCommandsExchange() {
        return new DirectExchange(SAGA_COMMANDS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange sagaRepliesExchange() {
        return new DirectExchange(SAGA_REPLIES_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange sagaDlxExchange() {
        return new FanoutExchange(SAGA_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue sagaRepliesQueue() {
        return QueueBuilder.durable(SAGA_REPLIES_QUEUE)
                .withArgument("x-dead-letter-exchange", SAGA_DLX_EXCHANGE)
                .withArgument("x-message-ttl", 60_000)
                .build();
    }

    @Bean
    public Queue sagaDeadLetterQueue() {
        return QueueBuilder.durable(SAGA_DLQ_QUEUE).build();
    }

    @Bean
    public Binding sagaRepliesBinding(
            @Qualifier("sagaRepliesQueue") Queue sagaRepliesQueue, DirectExchange sagaRepliesExchange) {
        return BindingBuilder.bind(sagaRepliesQueue)
                .to(sagaRepliesExchange)
                .with(SAGA_REPLIES_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(
            @Qualifier("sagaDeadLetterQueue") Queue sagaDeadLetterQueue, FanoutExchange sagaDlxExchange) {
        return new Binding(
                sagaDeadLetterQueue.getName(),
                Binding.DestinationType.QUEUE,
                sagaDlxExchange.getName(),
                "#",
                null);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        ObjectMapper mapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new Jackson2JsonMessageConverter(mapper);
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
                        log.warn("Saga command not confirmed: {}", cause);
                    }
                });
        template.setReturnsCallback(
                returned ->
                        log.warn(
                                "Saga command returned undeliverable: {} routing key: {}",
                                returned.getMessage(),
                                returned.getRoutingKey()));
        return template;
    }

    @Bean(name = "sagaListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory sagaListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        factory.setPrefetchCount(1);
        return factory;
    }
}
