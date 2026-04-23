package com.finflow.payment.saga;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for PaymentInitiated from RabbitMQ (same shape as transaction-service's saga initiator).
 *
 * <p>receiverAccountId is not in this message because FinFlow currently handles single-account debit
 * (payment to external). The sender account is debited and the system account is credited. This can
 * be extended for account-to-account transfers.
 */
public record PaymentInitiatedMessage(
        String transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        String correlationId,
        Instant initiatedAt) {}
