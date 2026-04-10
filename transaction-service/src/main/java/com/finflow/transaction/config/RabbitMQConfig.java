package com.finflow.transaction.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String PAYMENT_INITIATED_ROUTING_KEY = "payment.initiated";
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "payment.failed";
    public static final String PAYMENT_COMPLETED_QUEUE = "transaction.payment.completed";
    public static final String PAYMENT_FAILED_QUEUE = "transaction.payment.failed";
    public static final String PAYMENT_DLX_EXCHANGE = "payment.dlx";
    public static final String PAYMENT_DLQ_QUEUE = "payment.dead-letter";

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange paymentDlxExchange() {
        return new FanoutExchange(PAYMENT_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentCompletedQueue() {
        return QueueBuilder.durable(PAYMENT_COMPLETED_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_DLX_EXCHANGE)
                .withArgument("x-message-ttl", 60_000)
                .build();
    }

    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(PAYMENT_FAILED_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Queue paymentDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_DLQ_QUEUE).build();
    }

    @Bean
    public Binding paymentCompletedBinding(
            @Qualifier("paymentCompletedQueue") Queue paymentCompletedQueue,
            TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(paymentCompletedQueue)
                .to(paymentEventsExchange)
                .with(PAYMENT_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding paymentFailedBinding(
            @Qualifier("paymentFailedQueue") Queue paymentFailedQueue,
            TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(paymentFailedQueue)
                .to(paymentEventsExchange)
                .with(PAYMENT_FAILED_ROUTING_KEY);
    }

    /**
     * Binds the DLQ to the fanout DLX. For fanout exchanges RabbitMQ ignores the routing key; the
     * {@code "#"} requirement is represented as the conventional match-all pattern in docs only.
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
        return template;
    }
}
