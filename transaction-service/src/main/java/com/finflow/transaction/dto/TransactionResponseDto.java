package com.finflow.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponseDto(
        String id,
        String accountId,
        BigDecimal amount,
        String currency,
        String status,
        String description,
        String initiatedBy,
        String paymentId,
        String failureReason,
        String failureCode,
        String reversalReason,
        String reversedBy,
        String correlationId,
        Instant createdAt,
        Instant completedAt,
        Instant reversedAt,
        Instant updatedAt) {}
