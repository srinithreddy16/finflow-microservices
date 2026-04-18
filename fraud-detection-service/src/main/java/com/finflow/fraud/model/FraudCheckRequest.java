package com.finflow.fraud.model;

import java.math.BigDecimal;

/**
 * Domain input for synchronous fraud evaluation (aligned with gRPC {@code FraudCheckRequest}).
 */
public record FraudCheckRequest(
        String transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        String countryCode) {}
