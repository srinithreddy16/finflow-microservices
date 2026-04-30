package com.finflow.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.notification.consumer.KafkaNotificationConsumer;
import com.finflow.notification.consumer.KafkaNotificationConsumer.FraudDetectedEvent;
import com.finflow.notification.consumer.KafkaNotificationConsumer.TransactionKafkaEvent;
import com.finflow.notification.model.NotificationChannel;
import com.finflow.notification.service.NotificationRequest;
import com.finflow.notification.service.NotificationService;
import com.finflow.notification.template.FraudAlertTemplate;
import com.finflow.notification.template.PaymentFailedTemplate;
import com.finflow.notification.template.PaymentReceiptTemplate;
import com.finflow.notification.template.WelcomeEmailTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class KafkaNotificationConsumerTest {

    @Mock private NotificationService notificationService;
    @Mock private WelcomeEmailTemplate welcomeEmailTemplate;
    @Mock private PaymentReceiptTemplate paymentReceiptTemplate;
    @Mock private PaymentFailedTemplate paymentFailedTemplate;
    @Mock private FraudAlertTemplate fraudAlertTemplate;
    @Mock private Acknowledgment acknowledgment;

    @InjectMocks private KafkaNotificationConsumer consumer;

    @Test
    void consumeNotifyEvent_SendsReceipt_WhenPaymentCompleted() {
        TransactionKafkaEvent event =
                new TransactionKafkaEvent(
                        "evt-1",
                        "tx-1",
                        "acc-1",
                        BigDecimal.valueOf(100),
                        "USD",
                        "PAYMENT_RECEIPT",
                        "COMPLETED",
                        "corr-1",
                        "user@finflow.com",
                        Instant.now());
        NotificationRequest request =
                new NotificationRequest(
                        "acc-1",
                        "user@finflow.com",
                        null,
                        NotificationChannel.EMAIL,
                        "receipt",
                        "body",
                        "PAYMENT_RECEIPT",
                        "tx-1",
                        "corr-1");
        when(paymentReceiptTemplate.build(any(), any(), any(), any(), any(), any())).thenReturn(request);

        consumer.consumeNotifyEvent(event, acknowledgment);

        verify(notificationService).send(request);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeNotifyEvent_SendsAlert_WhenPaymentFailed() {
        TransactionKafkaEvent event =
                new TransactionKafkaEvent(
                        "evt-2",
                        "tx-2",
                        "acc-2",
                        BigDecimal.valueOf(50),
                        "USD",
                        "PAYMENT_FAILED",
                        "FAILED",
                        "corr-2",
                        "user@finflow.com",
                        Instant.now());
        NotificationRequest request =
                new NotificationRequest(
                        "acc-2",
                        "user@finflow.com",
                        null,
                        NotificationChannel.EMAIL,
                        "failed",
                        "body",
                        "PAYMENT_FAILED",
                        "tx-2",
                        "corr-2");
        when(paymentFailedTemplate.build(any(), any(), any(), any(), any())).thenReturn(request);

        consumer.consumeNotifyEvent(event, acknowledgment);

        verify(paymentFailedTemplate).build(any(), any(), any(), any(), any());
        verify(notificationService).send(request);
    }

    @Test
    void consumeFraudEvent_SendsAlert_WhenFlagged() {
        FraudDetectedEvent event =
                new FraudDetectedEvent(
                        "f-1",
                        "tx-9",
                        "acc-9",
                        BigDecimal.valueOf(250),
                        89,
                        true,
                        "user@finflow.com",
                        Instant.now());
        NotificationRequest request =
                new NotificationRequest(
                        "acc-9",
                        "user@finflow.com",
                        null,
                        NotificationChannel.EMAIL,
                        "fraud",
                        "body",
                        "FRAUD_ALERT",
                        "tx-9",
                        "corr-9");
        when(fraudAlertTemplate.build(any(), any(), any(), any(int.class), any())).thenReturn(request);

        consumer.consumeFraudEvent(event, acknowledgment);

        verify(fraudAlertTemplate).build(any(), any(), any(), any(int.class), any());
        verify(notificationService).send(request);
    }

    @Test
    void consumeFraudEvent_SkipsAlert_WhenNotFlagged() {
        FraudDetectedEvent event =
                new FraudDetectedEvent(
                        "f-2",
                        "tx-10",
                        "acc-10",
                        BigDecimal.valueOf(20),
                        10,
                        false,
                        "user@finflow.com",
                        Instant.now());

        consumer.consumeFraudEvent(event, acknowledgment);

        verify(notificationService, never()).send(any());
        verify(acknowledgment).acknowledge();
    }
}
