package com.finflow.graphql.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PlatformSummaryDto(
        long totalTransactionsToday,
        long completedTransactionsToday,
        BigDecimal totalVolumeToday,
        long fraudFlagsToday,
        BigDecimal fraudRateToday,
        long activeAccounts,
        Instant calculatedAt) {}
