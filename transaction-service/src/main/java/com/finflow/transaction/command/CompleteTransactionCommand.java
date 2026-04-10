package com.finflow.transaction.command;

import jakarta.validation.constraints.NotBlank;

public record CompleteTransactionCommand(
        @NotBlank String transactionId,
        @NotBlank String paymentId,
        String correlationId) {}
