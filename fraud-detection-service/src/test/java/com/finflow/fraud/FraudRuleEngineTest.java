package com.finflow.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.finflow.fraud.model.FraudCheckRequest;
import com.finflow.fraud.model.FraudRecord;
import com.finflow.fraud.model.FraudScore;
import com.finflow.fraud.repository.FraudRecordRepository;
import com.finflow.fraud.rules.AmountThresholdRule;
import com.finflow.fraud.rules.FraudRuleEngine;
import com.finflow.fraud.rules.GeolocationRule;
import com.finflow.fraud.rules.VelocityRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FraudRuleEngineTest {

    @Mock private FraudRecordRepository fraudRecordRepository;

    private AmountThresholdRule amountThresholdRule;
    private VelocityRule velocityRule;
    private GeolocationRule geolocationRule;
    private FraudRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        amountThresholdRule = new AmountThresholdRule();
        velocityRule = new VelocityRule(fraudRecordRepository);
        geolocationRule = new GeolocationRule(fraudRecordRepository);
        ruleEngine = new FraudRuleEngine(List.of(amountThresholdRule, velocityRule, geolocationRule));
    }

    @Test
    void evaluate_ReturnsClean_WhenAmountIsNormal() {
        FraudCheckRequest request =
                new FraudCheckRequest(
                        "tx-normal", "acc-001", new BigDecimal("500"), "USD", "US");
        when(fraudRecordRepository.countByAccountIdAndEvaluatedAtAfter(eq("acc-001"), any(Instant.class)))
                .thenReturn(0L);
        when(fraudRecordRepository.findTopByAccountIdOrderByEvaluatedAtDesc("acc-001"))
                .thenReturn(Optional.empty());

        FraudScore score = ruleEngine.evaluate(request);

        assertThat(score.flagged()).isFalse();
        assertThat(score.failureCode()).isEqualTo("CLEAN");
        assertThat(score.score()).isZero();
    }

    @Test
    void evaluate_FlagsTransaction_WhenAmountExceedsHighThreshold() {
        FraudCheckRequest request =
                new FraudCheckRequest(
                        "tx-high", "acc-001", new BigDecimal("15000"), "USD", "US");
        when(fraudRecordRepository.countByAccountIdAndEvaluatedAtAfter(eq("acc-001"), any(Instant.class)))
                .thenReturn(0L);
        when(fraudRecordRepository.findTopByAccountIdOrderByEvaluatedAtDesc("acc-001"))
                .thenReturn(Optional.empty());

        FraudScore score = ruleEngine.evaluate(request);

        assertThat(score.flagged()).isTrue();
        assertThat(score.triggeredRules()).contains("AMOUNT_THRESHOLD");
        assertThat(score.score()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void evaluate_FlagsTransaction_WhenVelocityExceeded() {
        FraudCheckRequest request =
                new FraudCheckRequest(
                        "tx-vel", "acc-001", new BigDecimal("500"), "USD", "US");
        when(fraudRecordRepository.countByAccountIdAndEvaluatedAtAfter(eq("acc-001"), any(Instant.class)))
                .thenReturn(6L);
        when(fraudRecordRepository.findTopByAccountIdOrderByEvaluatedAtDesc("acc-001"))
                .thenReturn(Optional.empty());

        FraudScore score = ruleEngine.evaluate(request);

        assertThat(score.flagged()).isTrue();
        assertThat(score.triggeredRules()).contains("VELOCITY");
        assertThat(score.score()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void evaluate_FlagsTransaction_WhenCountryMismatch() {
        FraudRecord prior =
                FraudRecord.builder()
                        .transactionId("prior-tx")
                        .accountId("acc-001")
                        .fraudScore(0)
                        .flagged(false)
                        .reason("clean")
                        .failureCode("CLEAN")
                        .triggeredRules("[]")
                        .amount(BigDecimal.ONE)
                        .currency("USD")
                        .countryCode("US")
                        .checkType("SYNC_GRPC")
                        .build();
        FraudCheckRequest request =
                new FraudCheckRequest(
                        "tx-geo", "acc-001", new BigDecimal("500"), "USD", "NG");
        when(fraudRecordRepository.countByAccountIdAndEvaluatedAtAfter(eq("acc-001"), any(Instant.class)))
                .thenReturn(0L);
        when(fraudRecordRepository.findTopByAccountIdOrderByEvaluatedAtDesc("acc-001"))
                .thenReturn(Optional.of(prior));

        FraudScore score = ruleEngine.evaluate(request);

        assertThat(score.flagged()).isTrue();
        assertThat(score.triggeredRules()).contains("GEOLOCATION");
    }

    @Test
    void evaluate_CombinesScores_WhenMultipleRulesTriggered() {
        FraudCheckRequest request =
                new FraudCheckRequest(
                        "tx-combo", "acc-001", new BigDecimal("15000"), "USD", "US");
        when(fraudRecordRepository.countByAccountIdAndEvaluatedAtAfter(eq("acc-001"), any(Instant.class)))
                .thenReturn(6L);
        when(fraudRecordRepository.findTopByAccountIdOrderByEvaluatedAtDesc("acc-001"))
                .thenReturn(Optional.empty());

        FraudScore score = ruleEngine.evaluate(request);

        assertThat(score.failureCode()).isEqualTo("COMBINED");
        assertThat(score.triggeredRules()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(score.score()).isLessThanOrEqualTo(100);
        assertThat(score.flagged()).isTrue();
    }

    @Test
    void evaluate_ReturnsClean_WhenSameCountry() {
        FraudRecord prior =
                FraudRecord.builder()
                        .transactionId("prior-tx")
                        .accountId("acc-001")
                        .fraudScore(0)
                        .flagged(false)
                        .reason("clean")
                        .failureCode("CLEAN")
                        .triggeredRules("[]")
                        .amount(BigDecimal.ONE)
                        .currency("USD")
                        .countryCode("US")
                        .checkType("SYNC_GRPC")
                        .build();
        FraudCheckRequest request =
                new FraudCheckRequest(
                        "tx-same-country", "acc-001", new BigDecimal("500"), "USD", "US");
        when(fraudRecordRepository.countByAccountIdAndEvaluatedAtAfter(eq("acc-001"), any(Instant.class)))
                .thenReturn(0L);
        when(fraudRecordRepository.findTopByAccountIdOrderByEvaluatedAtDesc("acc-001"))
                .thenReturn(Optional.of(prior));

        FraudScore score = ruleEngine.evaluate(request);

        assertThat(score.triggeredRules()).doesNotContain("GEOLOCATION");
        assertThat(score.flagged()).isFalse();
    }
}
