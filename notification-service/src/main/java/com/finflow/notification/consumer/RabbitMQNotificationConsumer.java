package com.finflow.notification.consumer;

import com.finflow.notification.model.NotificationChannel;
import com.finflow.notification.service.NotificationRequest;
import com.finflow.notification.service.NotificationService;
import com.finflow.notification.template.WelcomeEmailTemplate;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RabbitMQNotificationConsumer {

    private final NotificationService notificationService;
    private final WelcomeEmailTemplate welcomeEmailTemplate;

    public record SagaNotificationCommand(
            String sagaId,
            String commandType,
            String email,
            String firstName,
            String lastName,
            String accountId,
            String correlationId) {}

    @RabbitListener(
            queues = "saga.notification.commands",
            containerFactory = "notificationRabbitListenerFactory")
    public void handleSagaCommand(
            SagaNotificationCommand command,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws Exception {
        try {
            switch (command.commandType()) {
                case "SEND_WELCOME_EMAIL" -> {
                    NotificationRequest request =
                            welcomeEmailTemplate.build(
                                    command.email(),
                                    command.firstName(),
                                    command.lastName(),
                                    command.accountId(),
                                    command.sagaId(),
                                    command.correlationId());
                    notificationService.send(request);
                    log.info("Welcome email notification processed for: {}", command.email());
                }
                case "SEND_CANCELLATION_EMAIL" -> {
                    NotificationRequest request =
                            new NotificationRequest(
                                    command.accountId(),
                                    command.email(),
                                    null,
                                    NotificationChannel.EMAIL,
                                    "Onboarding Update",
                                    "Your onboarding request was cancelled. Please contact support if you need assistance.",
                                    "CANCELLATION_EMAIL",
                                    command.sagaId(),
                                    command.correlationId());
                    notificationService.send(request);
                }
                default -> log.warn("Unknown saga notification command: {}", command.commandType());
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("Failed to process saga notification: {}", ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
