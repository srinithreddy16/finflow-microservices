package com.finflow.fraud.rules;

import com.finflow.fraud.model.FraudCheckRequest;

/**
 * Strategy interface for fraud detection rules.
 *
 * <p>Each rule independently evaluates a transaction and returns a score contribution (0 = clean,
 * 100 = maximum risk). The {@link FraudRuleEngine} aggregates scores from all rules.
 */
public interface FraudRule {

    record RuleResult(boolean triggered, int score, String reason) {}

    RuleResult evaluate(FraudCheckRequest request, String accountId);

    String getRuleName();
}
