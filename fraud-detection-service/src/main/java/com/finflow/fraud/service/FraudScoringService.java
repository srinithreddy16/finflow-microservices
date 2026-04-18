package com.finflow.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.fraud.kafka.FraudEventPublisher;
import com.finflow.fraud.model.FraudCheckRequest;
import com.finflow.fraud.model.FraudRecord;
import com.finflow.fraud.model.FraudScore;
import com.finflow.fraud.repository.FraudRecordRepository;
import com.finflow.fraud.rules.FraudRuleEngine;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudScoringService {

    private static final String CHECK_TYPE_SYNC_GRPC = "SYNC_GRPC";

    private final FraudRuleEngine fraudRuleEngine;
    private final FraudRecordRepository fraudRecordRepository;
    private final FraudEventPublisher fraudEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public FraudScore evaluateAndPersist(FraudCheckRequest request, String checkType) {
        Optional<FraudRecord> existing =
                fraudRecordRepository.findByTransactionId(request.transactionId());
        if (existing.isPresent() && CHECK_TYPE_SYNC_GRPC.equals(checkType)) {
            log.debug("Returning cached fraud result for: {}", request.transactionId());
            return toFraudScore(existing.get());
        }

        FraudScore score = fraudRuleEngine.evaluate(request);

        String triggeredRulesJson;
        try {
            triggeredRulesJson = objectMapper.writeValueAsString(score.triggeredRules());
        } catch (JsonProcessingException e) {
            throw FinFlowException.internalError(
                    ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize triggered rules", e);
        }

        FraudRecord record =
                FraudRecord.builder()
                        .transactionId(request.transactionId())
                        .accountId(request.accountId())
                        .fraudScore(score.score())
                        .flagged(score.flagged())
                        .reason(score.reason())
                        .failureCode(score.failureCode())
                        .triggeredRules(triggeredRulesJson)
                        .amount(request.amount() != null ? request.amount() : BigDecimal.ZERO)
                        .currency(request.currency())
                        .countryCode(request.countryCode())
                        .checkType(checkType)
                        .build();
        fraudRecordRepository.save(record);

        if (score.flagged()) {
            fraudEventPublisher.publishFraudDetected(score, request);
        }

        log.info(
                "Fraud check completed for {}: score={}, flagged={}",
                request.transactionId(),
                score.score(),
                score.flagged());

        return score;
    }

    public FraudRecord getFraudRecord(String transactionId) {
        return fraudRecordRepository
                .findByTransactionId(transactionId)
                .orElseThrow(
                        () ->
                                FinFlowException.notFound(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "Fraud record not found for transaction: " + transactionId));
    }

    public List<FraudRecord> getFraudRecordsByAccount(String accountId) {
        return fraudRecordRepository.findByAccountIdOrderByEvaluatedAtDesc(accountId);
    }

    private FraudScore toFraudScore(FraudRecord record) {
        List<String> rules;
        try {
            rules =
                    objectMapper.readValue(
                            record.getTriggeredRules() == null
                                    ? "[]"
                                    : record.getTriggeredRules(),
                            new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            rules = Collections.emptyList();
        }
        return new FraudScore(
                record.getTransactionId(),
                record.getAccountId(),
                record.getFraudScore(),
                record.isFlagged(),
                record.getReason(),
                record.getFailureCode(),
                rules,
                record.getEvaluatedAt());
    }
}
