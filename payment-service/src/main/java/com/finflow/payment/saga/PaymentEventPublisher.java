package com.finflow.payment.saga;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes PaymentCompleted and PaymentFailed events back to RabbitMQ after processing. These
 * events are consumed by:
 *
 * <ul>
 *   <li>transaction-service (to update the transaction state)
 *   <li>notification-service (to send receipts/alerts)
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    private static final String COMPLETED_KEY = "payment.completed";
    private static final String FAILED_KEY = "payment.failed";

    private final RabbitTemplate rabbitTemplate;

    public record PaymentCompletedMessage(
            String transactionId,
            String paymentId,
            String correlationId,
            Instant completedAt,
            BigDecimal amount,
            String currency) {}

    public record PaymentFailedMessage(
            String transactionId,
            String reason,
            String failureCode,
            String correlationId,
            Instant failedAt) {}

    public void publishCompleted(
            String transactionId,
            String paymentId,
            String correlationId,
            BigDecimal amount,
            String currency) {
        try {
            PaymentCompletedMessage message =
                    new PaymentCompletedMessage(
                            transactionId,
                            paymentId,
                            correlationId,
                            Instant.now(),
                            amount,
                            currency);
            rabbitTemplate.convertAndSend(PAYMENT_EVENTS_EXCHANGE, COMPLETED_KEY, message);
            log.info(
                    "PaymentCompleted published for transaction: {}, paymentId: {}",
                    transactionId,
                    paymentId);
        } catch (AmqpException e) {
            log.error("Failed to publish PaymentCompleted for transaction: {}", transactionId, e);
        }
    }

    public void publishFailed(
            String transactionId, String reason, String failureCode, String correlationId) {
        try {
            PaymentFailedMessage message =
                    new PaymentFailedMessage(
                            transactionId,
                            reason,
                            failureCode,
                            correlationId,
                            Instant.now());
            rabbitTemplate.convertAndSend(PAYMENT_EVENTS_EXCHANGE, FAILED_KEY, message);
            log.warn(
                    "PaymentFailed published for transaction: {}, reason: {}",
                    transactionId,
                    reason);
        } catch (AmqpException e) {
            log.error("Failed to publish PaymentFailed for transaction: {}", transactionId, e);
        }
    }
}
