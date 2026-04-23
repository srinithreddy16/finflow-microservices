package com.finflow.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountBalanceDto(
        String accountId,
        BigDecimal balance,
        String currency,
        long totalTransactions,
        long completedTransactions,
        Instant calculatedAt) {}
