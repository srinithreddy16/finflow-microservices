package com.finflow.transaction.saga;

import static com.finflow.transaction.config.RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE;
import static com.finflow.transaction.config.RabbitMQConfig.PAYMENT_INITIATED_ROUTING_KEY;

import com.finflow.transaction.aggregate.TransactionAggregate;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Initiates the choreography saga by publishing PaymentInitiated to RabbitMQ after a transaction
 * is successfully created.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentSagaInitiator {

    public record PaymentInitiatedMessage(
            String transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            String correlationId,
            Instant initiatedAt) {}

    private final RabbitTemplate rabbitTemplate;

    public void initiatePayment(TransactionAggregate aggregate) {
        try {
            PaymentInitiatedMessage message =
                    new PaymentInitiatedMessage(
                            aggregate.getTransactionId(),
                            aggregate.getAccountId(),
                            aggregate.getAmount(),
                            aggregate.getCurrency(),
                            aggregate.getCorrelationId(),
                            Instant.now());

            rabbitTemplate.convertAndSend(
                    PAYMENT_EVENTS_EXCHANGE, PAYMENT_INITIATED_ROUTING_KEY, message);

            log.info(
                    "PaymentInitiated published for transaction: {} amount: {} {}",
                    aggregate.getTransactionId(),
                    aggregate.getAmount(),
                    aggregate.getCurrency());
        } catch (Exception e) {
            log.error(
                    "Failed to publish PaymentInitiated for transaction: {}",
                    aggregate.getTransactionId(),
                    e);
        }
    }
}
