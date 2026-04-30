package com.finflow.graphql.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyMetricsDto(
        LocalDate date,
        long totalTransactions,
        long completedTransactions,
        long failedTransactions,
        BigDecimal totalVolume,
        BigDecimal completedVolume,
        long fraudFlags,
        String currency) {}
