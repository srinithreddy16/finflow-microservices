package com.finflow.transaction.dto;

import jakarta.validation.constraints.NotBlank;

public record ReverseTransactionRequest(
        @NotBlank String reversalReason, String correlationId) {}
