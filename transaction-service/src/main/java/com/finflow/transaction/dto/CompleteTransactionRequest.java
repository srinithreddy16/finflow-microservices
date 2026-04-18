package com.finflow.transaction.dto;

public record CompleteTransactionRequest(String paymentId, String correlationId) {}
