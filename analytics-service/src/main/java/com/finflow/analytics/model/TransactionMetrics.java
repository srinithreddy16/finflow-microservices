package com.finflow.analytics.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionMetrics(
        String accountId,
        long totalTransactions,
        long completedTransactions,
        long failedTransactions,
        long reversedTransactions,
        BigDecimal totalVolume,
        BigDecimal completedVolume,
        long fraudFlags,
        BigDecimal avgTransactionAmount,
        String currency,
        LocalDate date) {

    public static TransactionMetrics from(AnalyticsReadModel model) {
        return new TransactionMetrics(
                model.getAccountId(),
                model.getTotalTransactions(),
                model.getCompletedTransactions(),
                model.getFailedTransactions(),
                model.getReversedTransactions(),
                model.getTotalVolume(),
                model.getCompletedVolume(),
                model.getFraudFlags(),
                model.getAvgTransactionAmount(),
                model.getCurrency(),
                model.getDate());
    }
}
