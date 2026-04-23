package com.finflow.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponseDto(
        String id,
        String transactionId,
        String senderAccountId,
        String receiverAccountId,
        BigDecimal amount,
        String currency,
        String status,
        String failureReason,
        String correlationId,
        Instant createdAt,
        Instant completedAt,
        Instant updatedAt) {}
