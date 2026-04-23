package com.finflow.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryDto(
        String id,
        String paymentId,
        String accountId,
        String entryType,
        BigDecimal amount,
        String currency,
        String description,
        BigDecimal runningBalance,
        Instant createdAt) {}
