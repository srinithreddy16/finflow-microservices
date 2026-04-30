package com.finflow.graphql.client.dto;

import java.time.Instant;

public record FraudScoreDto(
        String transactionId,
        int score,
        boolean flagged,
        String reason,
        String failureCode,
        Instant evaluatedAt) {}
