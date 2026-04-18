package com.finflow.fraud.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.common.util.IdGenerator;
import com.finflow.fraud.model.FraudCheckRequest;
import com.finflow.fraud.model.FraudRecord;
import com.finflow.fraud.model.FraudScore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes fraud-events to Kafka when a transaction is flagged. Both analytics-service and
 * notification-service consume this topic.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FraudEventPublisher {

    private static final String FRAUD_EVENTS_TOPIC = "fraud-events";
    private static final String CHECK_TYPE_SYNC_GRPC = "SYNC_GRPC";
    private static final String CHECK_TYPE_ASYNC_KAFKA = "ASYNC_KAFKA";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public record FraudDetectedEvent(
            String eventId,
            String transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            int fraudScore,
            String reason,
            String failureCode,
            List<String> triggeredRules,
            boolean flagged,
            String checkType,
            Instant occurredOn) {}

    public void publishFraudDetected(FraudScore score, FraudCheckRequest request) {
        BigDecimal amount =
                request.amount() != null ? request.amount() : BigDecimal.ZERO;
        FraudDetectedEvent event =
                new FraudDetectedEvent(
                        IdGenerator.generate(),
                        request.transactionId(),
                        request.accountId(),
                        amount,
                        request.currency(),
                        score.score(),
                        score.reason(),
                        score.failureCode(),
                        score.triggeredRules(),
                        score.flagged(),
                        CHECK_TYPE_SYNC_GRPC,
                        Instant.now());

        kafkaTemplate
                .send(FRAUD_EVENTS_TOPIC, event.transactionId(), event)
                .whenComplete(
                        (result, ex) -> {
                            if (ex != null) {
                                log.error(
                                        "Failed to publish fraud event for transaction: {}",
                                        event.transactionId(),
                                        ex);
                            } else {
                                log.info(
                                        "Fraud event published for transaction: {}, flagged: {}",
                                        event.transactionId(),
                                        event.flagged());
                            }
                        });
    }

    public void publishFraudAlert(FraudRecord record) {
        if (!record.isFlagged()) {
            return;
        }
        log.warn(
                "Publishing fraud alert for account: {} transaction: {}",
                record.getAccountId(),
                record.getTransactionId());

        List<String> triggeredRules = parseTriggeredRules(record.getTriggeredRules());

        FraudDetectedEvent event =
                new FraudDetectedEvent(
                        IdGenerator.generate(),
                        record.getTransactionId(),
                        record.getAccountId(),
                        record.getAmount() != null ? record.getAmount() : BigDecimal.ZERO,
                        record.getCurrency(),
                        record.getFraudScore(),
                        record.getReason(),
                        record.getFailureCode(),
                        triggeredRules,
                        record.isFlagged(),
                        CHECK_TYPE_ASYNC_KAFKA,
                        record.getEvaluatedAt() != null
                                ? record.getEvaluatedAt()
                                : Instant.now());

        kafkaTemplate
                .send(FRAUD_EVENTS_TOPIC, event.transactionId(), event)
                .whenComplete(
                        (result, ex) -> {
                            if (ex != null) {
                                log.error(
                                        "Failed to publish fraud event for transaction: {}",
                                        event.transactionId(),
                                        ex);
                            } else {
                                log.info(
                                        "Fraud event published for transaction: {}, flagged: {}",
                                        event.transactionId(),
                                        event.flagged());
                            }
                        });
    }

    private List<String> parseTriggeredRules(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Could not parse triggeredRules JSON, using empty list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
