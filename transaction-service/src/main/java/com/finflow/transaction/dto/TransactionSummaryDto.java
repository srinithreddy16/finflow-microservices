package com.finflow.transaction.dto;

import java.math.BigDecimal;

public record TransactionSummaryDto(
        String accountId,
        long totalTransactions,
        long completedTransactions,
        long failedTransactions,
        long pendingTransactions,
        long reversedTransactions,
        BigDecimal totalCompletedAmount,
        String currency) {}
