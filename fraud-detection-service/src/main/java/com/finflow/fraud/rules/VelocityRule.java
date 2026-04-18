package com.finflow.fraud.rules;

import com.finflow.fraud.model.FraudCheckRequest;
import com.finflow.fraud.repository.FraudRecordRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class VelocityRule implements FraudRule {

    private static final int MAX_TRANSACTIONS_PER_MINUTE = 5;
    private static final int VELOCITY_WINDOW_SECONDS = 60;

    private final FraudRecordRepository fraudRecordRepository;

    @Override
    public String getRuleName() {
        return "VELOCITY";
    }

    @Override
    public RuleResult evaluate(FraudCheckRequest request, String accountId) {
        Instant windowStart = Instant.now().minusSeconds(VELOCITY_WINDOW_SECONDS);
        long recentCount =
                fraudRecordRepository.countByAccountIdAndEvaluatedAtAfter(accountId, windowStart);

        if (recentCount >= MAX_TRANSACTIONS_PER_MINUTE) {
            return new RuleResult(
                    true,
                    90,
                    "Velocity limit exceeded: "
                            + recentCount
                            + " transactions in last 60 seconds");
        }
        if (recentCount >= 3) {
            return new RuleResult(
                    true,
                    40,
                    "Elevated velocity: "
                            + recentCount
                            + " transactions in last 60 seconds");
        }
        return new RuleResult(false, 0, "Transaction velocity within normal range");
    }
}
