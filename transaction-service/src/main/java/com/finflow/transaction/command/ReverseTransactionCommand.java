package com.finflow.transaction.command;

import jakarta.validation.constraints.NotBlank;

public record ReverseTransactionCommand(
        @NotBlank String transactionId,
        @NotBlank String reversalReason,
        @NotBlank String reversedBy,
        String correlationId) {}
