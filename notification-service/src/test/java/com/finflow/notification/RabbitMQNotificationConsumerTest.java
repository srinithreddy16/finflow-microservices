package com.finflow.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.notification.consumer.RabbitMQNotificationConsumer;
import com.finflow.notification.consumer.RabbitMQNotificationConsumer.SagaNotificationCommand;
import com.finflow.notification.model.NotificationChannel;
import com.finflow.notification.service.NotificationRequest;
import com.finflow.notification.service.NotificationService;
import com.finflow.notification.template.WelcomeEmailTemplate;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RabbitMQNotificationConsumerTest {

    @Mock private NotificationService notificationService;
    @Mock private WelcomeEmailTemplate welcomeEmailTemplate;
    @Mock private Channel channel;

    @InjectMocks private RabbitMQNotificationConsumer consumer;

    @Test
    void handleSagaCommand_SendsWelcomeEmail_OnWelcomeCommand() throws Exception {
        SagaNotificationCommand command =
                new SagaNotificationCommand(
                        "saga-1",
                        "SEND_WELCOME_EMAIL",
                        "user@finflow.com",
                        "Jane",
                        "Doe",
                        "acc-1",
                        "corr-1");
        NotificationRequest request =
                new NotificationRequest(
                        "acc-1",
                        "user@finflow.com",
                        null,
                        NotificationChannel.EMAIL,
                        "welcome",
                        "body",
                        "WELCOME",
                        "saga-1",
                        "corr-1");
        when(welcomeEmailTemplate.build(any(), any(), any(), any(), any(), any())).thenReturn(request);

        consumer.handleSagaCommand(command, channel, 1L);

        verify(notificationService).send(request);
        verify(channel).basicAck(1L, false);
    }

    @Test
    void handleSagaCommand_Nacks_WhenServiceThrows() throws Exception {
        SagaNotificationCommand command =
                new SagaNotificationCommand(
                        "saga-2",
                        "SEND_WELCOME_EMAIL",
                        "user@finflow.com",
                        "John",
                        "Smith",
                        "acc-2",
                        "corr-2");
        NotificationRequest request =
                new NotificationRequest(
                        "acc-2",
                        "user@finflow.com",
                        null,
                        NotificationChannel.EMAIL,
                        "welcome",
                        "body",
                        "WELCOME",
                        "saga-2",
                        "corr-2");
        when(welcomeEmailTemplate.build(any(), any(), any(), any(), any(), any())).thenReturn(request);
        doThrow(new RuntimeException("send failure"))
                .when(notificationService)
                .send(any(NotificationRequest.class));

        consumer.handleSagaCommand(command, channel, 1L);

        verify(channel).basicNack(1L, false, false);
    }
}
