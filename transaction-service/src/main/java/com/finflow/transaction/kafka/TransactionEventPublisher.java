package com.finflow.transaction.kafka;

import com.finflow.common.util.IdGenerator;
import com.finflow.transaction.aggregate.TransactionAggregate;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes transaction domain events to Kafka after they are committed to the EventStore.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private static final String TRANSACTIONS_TOPIC = "transactions";
    private static final String ANALYTICS_TOPIC = "analytics";
    private static final String AUDIT_LOG_TOPIC = "audit-log";
    private static final String NOTIFY_EVENTS_TOPIC = "notify-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishCreated(TransactionAggregate aggregate) {
        String transactionId = aggregate.getTransactionId();
        Instant occurredOn = Instant.now();
        String correlationId = aggregate.getCorrelationId();

        TransactionKafkaEvent payload =
                new TransactionKafkaEvent(
                        IdGenerator.generate(),
                        transactionId,
                        aggregate.getAccountId(),
                        aggregate.getAmount(),
                        aggregate.getCurrency(),
                        "TRANSACTION_CREATED",
                        "PENDING",
                        correlationId,
                        occurredOn);

        send(TRANSACTIONS_TOPIC, transactionId, payload);
        send(ANALYTICS_TOPIC, transactionId, payload);
        send(AUDIT_LOG_TOPIC, transactionId, payload);

        TransactionKafkaEvent notifyPayload =
                new TransactionKafkaEvent(
                        IdGenerator.generate(),
                        transactionId,
                        aggregate.getAccountId(),
                        aggregate.getAmount(),
                        aggregate.getCurrency(),
                        "TRANSACTION_PENDING",
                        "PENDING",
                        correlationId,
                        occurredOn);
        send(NOTIFY_EVENTS_TOPIC, transactionId, notifyPayload);

        log.info("Published TRANSACTION_CREATED events for: {}", transactionId);
    }

    public void publishCompleted(TransactionAggregate aggregate) {
        String transactionId = aggregate.getTransactionId();
        Instant occurredOn = Instant.now();
        String correlationId = aggregate.getCorrelationId();

        TransactionKafkaEvent payload =
                new TransactionKafkaEvent(
                        IdGenerator.generate(),
                        transactionId,
                        aggregate.getAccountId(),
                        aggregate.getAmount(),
                        aggregate.getCurrency(),
                        "TRANSACTION_COMPLETED",
                        "COMPLETED",
                        correlationId,
                        occurredOn);

        send(TRANSACTIONS_TOPIC, transactionId, payload);
        send(ANALYTICS_TOPIC, transactionId, payload);
        send(AUDIT_LOG_TOPIC, transactionId, payload);

        TransactionKafkaEvent notifyPayload =
                new TransactionKafkaEvent(
                        IdGenerator.generate(),
                        transactionId,
                        aggregate.getAccountId(),
                        aggregate.getAmount(),
                        aggregate.getCurrency(),
                        "PAYMENT_RECEIPT",
                        "COMPLETED",
                        correlationId,
                        occurredOn);
        send(NOTIFY_EVENTS_TOPIC, transactionId, notifyPayload);
    }

    public void publishFailed(TransactionAggregate aggregate) {
        String transactionId = aggregate.getTransactionId();
        Instant occurredOn = Instant.now();
        String correlationId = aggregate.getCorrelationId();

        TransactionKafkaEvent payload =
                new TransactionKafkaEvent(
                        IdGenerator.generate(),
                        transactionId,
                        aggregate.getAccountId(),
                        aggregate.getAmount(),
                        aggregate.getCurrency(),
                        "TRANSACTION_FAILED",
                        "FAILED",
                        correlationId,
                        occurredOn);

        send(TRANSACTIONS_TOPIC, transactionId, payload);
        send(ANALYTICS_TOPIC, transactionId, payload);
        send(AUDIT_LOG_TOPIC, transactionId, payload);

        TransactionKafkaEvent notifyPayload =
                new TransactionKafkaEvent(
                        IdGenerator.generate(),
                        transactionId,
                        aggregate.getAccountId(),
                        aggregate.getAmount(),
                        aggregate.getCurrency(),
                        "TRANSACTION_FAILED",
                        "FAILED",
                        correlationId,
                        occurredOn);
        send(NOTIFY_EVENTS_TOPIC, transactionId, notifyPayload);
    }

    public void publishReversed(TransactionAggregate aggregate) {
        String transactionId = aggregate.getTransactionId();
        Instant occurredOn = Instant.now();
        String correlationId = aggregate.getCorrelationId();

        TransactionKafkaEvent payload =
                new TransactionKafkaEvent(
                        IdGenerator.generate(),
                        transactionId,
                        aggregate.getAccountId(),
                        aggregate.getAmount(),
                        aggregate.getCurrency(),
                        "TRANSACTION_REVERSED",
                        "REVERSED",
                        correlationId,
                        occurredOn);

        send(TRANSACTIONS_TOPIC, transactionId, payload);
        send(ANALYTICS_TOPIC, transactionId, payload);
        send(AUDIT_LOG_TOPIC, transactionId, payload);

        TransactionKafkaEvent notifyPayload =
                new TransactionKafkaEvent(
                        IdGenerator.generate(),
                        transactionId,
                        aggregate.getAccountId(),
                        aggregate.getAmount(),
                        aggregate.getCurrency(),
                        "TRANSACTION_REVERSED",
                        "REVERSED",
                        correlationId,
                        occurredOn);
        send(NOTIFY_EVENTS_TOPIC, transactionId, notifyPayload);
    }

    private void send(String topic, String key, TransactionKafkaEvent payload) {
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
