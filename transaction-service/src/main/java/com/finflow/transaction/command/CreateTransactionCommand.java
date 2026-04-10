package com.finflow.transaction.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Command to create a new transaction.
 * The transactionId is pre-generated to support idempotency.
 * If the same transactionId is submitted twice, the second
 * request is rejected with a duplicate error.
 */
public record CreateTransactionCommand(
        @NotBlank String transactionId,
        @NotBlank String accountId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        String description,
        @NotBlank String initiatedBy,
        String correlationId) {}
