package com.finflow.notification.consumer;

import com.finflow.notification.service.NotificationRequest;
import com.finflow.notification.service.NotificationService;
import com.finflow.notification.template.FraudAlertTemplate;
import com.finflow.notification.template.PaymentFailedTemplate;
import com.finflow.notification.template.PaymentReceiptTemplate;
import com.finflow.notification.template.WelcomeEmailTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaNotificationConsumer {

    private final NotificationService notificationService;
    private final WelcomeEmailTemplate welcomeEmailTemplate;
    private final PaymentReceiptTemplate paymentReceiptTemplate;
    private final PaymentFailedTemplate paymentFailedTemplate;
    private final FraudAlertTemplate fraudAlertTemplate;

    public record TransactionKafkaEvent(
            String eventId,
            String transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            String eventType,
            String status,
            String correlationId,
            String recipientEmail,
            Instant occurredOn) {}

    public record FraudDetectedEvent(
            String eventId,
            String transactionId,
            String accountId,
            BigDecimal amount,
            int fraudScore,
            boolean flagged,
            String recipientEmail,
            Instant occurredOn) {}

    @KafkaListener(
            topics = "notify-events",
            groupId = "notification-service-kafka",
            containerFactory = "notificationKafkaListenerFactory")
    public void consumeNotifyEvent(TransactionKafkaEvent event, Acknowledgment acknowledgment) {
        try {
            switch (event.eventType()) {
                case "PAYMENT_RECEIPT", "TRANSACTION_COMPLETED" -> {
                    NotificationRequest request =
                            paymentReceiptTemplate.build(
                                    event.recipientEmail(),
                                    event.accountId(),
                                    event.transactionId(),
                                    event.amount(),
                                    event.currency(),
                                    event.correlationId());
                    notificationService.send(request);
                }
                case "PAYMENT_FAILED", "TRANSACTION_FAILED" -> {
                    NotificationRequest request =
                            paymentFailedTemplate.build(
                                    event.recipientEmail(),
                                    event.accountId(),
                                    event.transactionId(),
                                    "Transaction processing failed",
                                    event.correlationId());
                    notificationService.send(request);
                }
                case "TRANSACTION_PENDING" ->
                        log.debug("Transaction pending -- no notification needed");
                default -> log.warn("Unknown notify event type: {}", event.eventType());
            }
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process notify event: {}", ex.getMessage(), ex);
        }
    }

    @KafkaListener(
            topics = "fraud-events",
            groupId = "notification-service-kafka",
            containerFactory = "notificationKafkaListenerFactory")
    public void consumeFraudEvent(FraudDetectedEvent event, Acknowledgment acknowledgment) {
        try {
            if (!event.flagged()) {
                acknowledgment.acknowledge();
                return;
            }

            NotificationRequest request =
                    fraudAlertTemplate.build(
                            event.recipientEmail(),
                            event.accountId(),
                            event.transactionId(),
                            event.fraudScore(),
                            null);
            notificationService.send(request);
            acknowledgment.acknowledge();
            log.warn("Fraud alert notification sent for account: {}", event.accountId());
        } catch (Exception ex) {
            log.error("Failed to process notify event: {}", ex.getMessage(), ex);
        }
    }
}
