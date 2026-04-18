package com.finflow.fraud.rules;

import com.finflow.fraud.model.FraudCheckRequest;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class AmountThresholdRule implements FraudRule {

    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = BigDecimal.valueOf(10_000.0);
    private static final BigDecimal VERY_HIGH_AMOUNT_THRESHOLD = BigDecimal.valueOf(50_000.0);

    @Override
    public String getRuleName() {
        return "AMOUNT_THRESHOLD";
    }

    @Override
    public RuleResult evaluate(FraudCheckRequest request, String accountId) {
        BigDecimal amount = request.amount() != null ? request.amount() : BigDecimal.ZERO;
        if (amount.compareTo(VERY_HIGH_AMOUNT_THRESHOLD) > 0) {
            return new RuleResult(
                    true,
                    80,
                    "Transaction amount exceeds $50,000 threshold");
        }
        if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
            return new RuleResult(
                    true,
                    70,
                    "Transaction amount exceeds $10,000 threshold");
        }
        return new RuleResult(false, 0, "Amount within normal range");
    }
}
