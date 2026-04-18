package com.finflow.fraud.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a fraud evaluation.
 *
 * <p>Score ranges from 0 (definitely clean) to 100 (definitely fraud). Transactions with score
 * &gt;= 70 are flagged automatically. Score is computed by aggregating individual rule scores.
 */
public record FraudScore(
        String transactionId,
        String accountId,
        int score,
        boolean flagged,
        String reason,
        String failureCode,
        List<String> triggeredRules,
        Instant evaluatedAt) {

    public static FraudScore clean(String transactionId, String accountId) {
        return new FraudScore(
                transactionId,
                accountId,
                0,
                false,
                "No fraud indicators detected",
                "CLEAN",
                Collections.emptyList(),
                Instant.now());
    }

    public static FraudScore flagged(
            String transactionId,
            String accountId,
            int score,
            String reason,
            String failureCode,
            List<String> triggeredRules) {
        return new FraudScore(
                transactionId,
                accountId,
                score,
                true,
                reason,
                failureCode,
                List.copyOf(triggeredRules),
                Instant.now());
    }
}
