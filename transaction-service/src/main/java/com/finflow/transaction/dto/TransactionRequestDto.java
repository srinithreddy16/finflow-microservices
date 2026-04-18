package com.finflow.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request DTO for creating a new transaction.
 *
 * <p>Note: {@code transactionId} is NOT in this DTO — it is generated server-side by the
 * {@link com.finflow.transaction.command.TransactionCommandHandler} to ensure uniqueness and
 * idempotency.
 */
public record TransactionRequestDto(
        @NotBlank String accountId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 500) String description) {}
