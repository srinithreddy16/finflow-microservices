package com.finflow.account.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/*
RabbitMQ is a post office. This config file is setting up the post office infrastructure
— the sorting rooms (exchanges), the mailboxes (queues), and
the rules for which mail goes to which mailbox (bindings).
Without this config, your services have nowhere to send messages and nowhere to listen
 */

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String SAGA_COMMANDS_EXCHANGE = "saga.commands";
    public static final String SAGA_REPLIES_EXCHANGE = "saga.replies";
    public static final String ACCOUNT_COMMANDS_QUEUE = "saga.account.commands";
    public static final String SAGA_REPLIES_QUEUE = "saga.replies.queue";
    public static final String SAGA_DLX_EXCHANGE = "saga.dlx";
    public static final String SAGA_DLQ_QUEUE = "saga.dead-letter";
    public static final String SAGA_ACCOUNT_COMPENSATION_QUEUE = "saga.account.compensation";

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
    public Queue accountCommandsQueue() {
        return QueueBuilder.durable(ACCOUNT_COMMANDS_QUEUE)
                .withArgument("x-dead-letter-exchange", SAGA_DLX_EXCHANGE)
                .withArgument("x-message-ttl", 30000)
                .build();
    }

    @Bean
    public Queue sagaRepliesQueue() {
        return QueueBuilder.durable(SAGA_REPLIES_QUEUE).build();
    }

    @Bean
    public Queue sagaDeadLetterQueue() {
        return QueueBuilder.durable(SAGA_DLQ_QUEUE).build();
    }

    @Bean
    public Queue sagaAccountCompensationQueue() {
        return QueueBuilder.durable(SAGA_ACCOUNT_COMPENSATION_QUEUE).build();
    }

    @Bean
    public Binding accountCommandsBinding(
            Queue accountCommandsQueue, DirectExchange sagaCommandsExchange) {
        return BindingBuilder.bind(accountCommandsQueue)
                .to(sagaCommandsExchange)
                .with("account.commands");
    }

    @Bean
    public Binding sagaRepliesBinding(
            Queue sagaRepliesQueue, DirectExchange sagaRepliesExchange) {
        return BindingBuilder.bind(sagaRepliesQueue)
                .to(sagaRepliesExchange)
                .with("saga.replies");
    }

    @Bean
    public Binding deadLetterBinding(Queue sagaDeadLetterQueue, FanoutExchange sagaDlxExchange) {
        return BindingBuilder.bind(sagaDeadLetterQueue).to(sagaDlxExchange);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {  //  attaches the JSON converter so all sends/receives automatically serialize/deserialize.
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) { // RabbitTemplate is the object your services use to actually send messages. Think of it as the stamp-and-post machine. Everywhere you inject RabbitTemplate and call rabbitTemplate.convertAndSend(exchange, routingKey, message) — that is using this bean. ConnectionFactory is injected by Spring — it is auto-configured from your application.yml RabbitMQ host/port/credentials. You do not create it manually.
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jacksonMessageConverter());
        rabbitTemplate.setMandatory(true);  // setMandatory(true) — this is important. It tells RabbitMQ: "If a message cannot be routed to any queue (no binding matches), return it to the sender as an error instead of silently dropping it." Without this, if you make a typo in a routing key, the message disappears silently. With mandatory = true, you get an exception immediately and can catch the problem.
        return rabbitTemplate;
    }
}
