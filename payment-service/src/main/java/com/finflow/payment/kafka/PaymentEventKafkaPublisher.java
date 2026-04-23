package com.finflow.payment.kafka;

import com.finflow.common.util.IdGenerator;
import com.finflow.payment.model.Payment;
import com.finflow.payment.saga.PaymentInitiatedMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes payment events to Kafka topics after processing. This is the Kafka producer side that
 * analytics-service, notification-service, and audit-log consumers read from.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventKafkaPublisher {

    private static final String ANALYTICS_TOPIC = "analytics";
    private static final String AUDIT_LOG_TOPIC = "audit-log";
    private static final String NOTIFY_EVENTS_TOPIC = "notify-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public record PaymentKafkaEvent(
            String eventId,
            String paymentId,
            String transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            String eventType,
            String status,
            String correlationId,
            Instant occurredOn) {}

    public void publishPaymentCompleted(Payment payment, PaymentInitiatedMessage message) {
        Objects.requireNonNull(message, "message");
        PaymentKafkaEvent payload =
                new PaymentKafkaEvent(
                        IdGenerator.generate(),
                        payment.getId(),
                        payment.getTransactionId(),
                        payment.getSenderAccountId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        "PAYMENT_COMPLETED",
                        "COMPLETED",
                        payment.getCorrelationId(),
                        Instant.now());

        String key = payment.getTransactionId();
        send(ANALYTICS_TOPIC, key, payload);
        send(AUDIT_LOG_TOPIC, key, payload);
        send(NOTIFY_EVENTS_TOPIC, key, payload);

        log.info("Kafka events published for completed payment: {}", key);
    }

    public void publishPaymentFailed(PaymentInitiatedMessage message, String failureReason) {
        PaymentKafkaEvent payload =
                new PaymentKafkaEvent(
                        IdGenerator.generate(),
                        "",
                        message.transactionId(),
                        message.accountId(),
                        message.amount(),
                        message.currency(),
                        "PAYMENT_FAILED",
                        "FAILED",
                        message.correlationId(),
                        Instant.now());

        String key = message.transactionId();
        send(ANALYTICS_TOPIC, key, payload);
        send(AUDIT_LOG_TOPIC, key, payload);
        send(NOTIFY_EVENTS_TOPIC, key, payload);

        log.warn(
                "Kafka events published for failed payment: {} reason: {}",
                message.transactionId(),
                failureReason);
    }

    private void send(String topic, String key, PaymentKafkaEvent payload) {
        kafkaTemplate
                .send(topic, key, payload)
                .whenComplete(
                        (result, ex) -> {
                            if (ex != null) {
                                log.error(
                                        "Failed to publish to Kafka topic {} for key {}: {}",
                                        topic,
                                        key,
                                        ex.getMessage(),
                                        ex);
                            }
                        });
    }
}
