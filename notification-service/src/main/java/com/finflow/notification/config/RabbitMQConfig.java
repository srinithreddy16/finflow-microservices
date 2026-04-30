package com.finflow.notification.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String NOTIFICATION_COMMANDS_QUEUE = "saga.notification.commands";
    public static final String SAGA_COMMANDS_EXCHANGE = "saga.commands";
    public static final String NOTIFY_WELCOME_ROUTING_KEY = "saga.notify.welcome";

    @Bean
    public Queue notificationCommandsQueue() {
        return QueueBuilder.durable(NOTIFICATION_COMMANDS_QUEUE).build();
    }

    @Bean
    public DirectExchange sagaCommandsExchange() {
        return new DirectExchange(SAGA_COMMANDS_EXCHANGE, true, false);
    }

    @Bean
    public Binding notificationCommandsBinding(
            Queue notificationCommandsQueue, DirectExchange sagaCommandsExchange) {
        return BindingBuilder.bind(notificationCommandsQueue)
                .to(sagaCommandsExchange)
                .with(NOTIFY_WELCOME_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean(name = "notificationRabbitListenerFactory")
    public SimpleRabbitListenerContainerFactory notificationRabbitListenerFactory(
            ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jacksonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        return factory;
    }
}
