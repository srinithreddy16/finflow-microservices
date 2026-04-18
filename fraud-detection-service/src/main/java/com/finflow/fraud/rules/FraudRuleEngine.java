package com.finflow.fraud.rules;

import com.finflow.fraud.model.FraudCheckRequest;
import com.finflow.fraud.model.FraudScore;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudRuleEngine {

    private static final int FRAUD_SCORE_THRESHOLD = 70;

    private final List<FraudRule> rules;

    public FraudScore evaluate(FraudCheckRequest request) {
        log.info(
                "Evaluating {} rules for transaction: {}",
                rules.size(),
                request.transactionId());

        List<FraudRule.RuleResult> results =
                rules.stream()
                        .map(rule -> rule.evaluate(request, request.accountId()))
                        .collect(Collectors.toList());

        List<String> triggeredRules =
                IntStream.range(0, rules.size())
                        .filter(i -> results.get(i).triggered())
                        .mapToObj(i -> rules.get(i).getRuleName())
                        .collect(Collectors.toList());

        int totalScore =
                results.stream().mapToInt(FraudRule.RuleResult::score).sum();
        totalScore = Math.min(totalScore, 100);

        String reason =
                IntStream.range(0, results.size())
                        .filter(i -> results.get(i).triggered())
                        .mapToObj(i -> results.get(i).reason())
                        .collect(Collectors.joining("; "));

        String failureCode;
        if (triggeredRules.size() > 1) {
            failureCode = "COMBINED";
        } else if (triggeredRules.size() == 1) {
            failureCode = triggeredRules.getFirst();
        } else {
            failureCode = "CLEAN";
        }

        if (totalScore >= FRAUD_SCORE_THRESHOLD) {
            log.warn(
                    "Transaction {} FLAGGED: score={}, rules={}",
                    request.transactionId(),
                    totalScore,
                    triggeredRules);
            return FraudScore.flagged(
                    request.transactionId(),
                    request.accountId(),
                    totalScore,
                    reason.isEmpty() ? "Fraud indicators detected" : reason,
                    failureCode,
                    triggeredRules);
        }

        log.info(
                "Transaction {} clean: score={}",
                request.transactionId(),
                totalScore);
        return FraudScore.clean(request.transactionId(), request.accountId());
    }
}
