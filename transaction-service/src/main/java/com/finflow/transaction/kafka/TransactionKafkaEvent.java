package com.finflow.transaction.kafka;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionKafkaEvent(
        String eventId,
        String transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        String eventType,
        String status,
        String correlationId,
        Instant occurredOn) {}
