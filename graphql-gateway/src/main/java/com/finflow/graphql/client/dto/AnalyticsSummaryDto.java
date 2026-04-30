package com.finflow.graphql.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AnalyticsSummaryDto(
        String accountId,
        long totalTransactions,
        long completedTransactions,
        long failedTransactions,
        long reversedTransactions,
        BigDecimal totalVolume,
        BigDecimal completedVolume,
        long fraudFlags,
        BigDecimal fraudRate,
        BigDecimal avgTransactionAmount,
        String currency,
        LocalDate fromDate,
        LocalDate toDate,
        Instant calculatedAt) {}
