package com.finflow.fraud.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.fraud.repository.FraudRecordRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Internal Kafka consumer for fraud-events topic.
 *
 * <p>The fraud service consumes its own events to:
 *
 * <ol>
 *   <li>Detect patterns across multiple transactions (velocity at account level)
 *   <li>Generate internal fraud alerts for high-risk accounts
 *   <li>Update internal monitoring counters
 * </ol>
 *
 * <p>Consumer group: fraud-detection-internal This is separate from analytics-service and
 * notification-service which have their own consumer groups for the same topic.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FraudEventConsumer {

    /**
     * Incoming {@code fraud-events} payload (matches producer JSON; unknown fields such as
     * {@code triggeredRules} are ignored).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FraudEventMessage(
            String eventId,
            String transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            int fraudScore,
            String reason,
            String failureCode,
            boolean flagged,
            String checkType,
            Instant occurredOn) {}

    private final FraudRecordRepository fraudRecordRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "fraud-events",
            groupId = "fraud-detection-internal",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeFraudEvent(
            @Payload Object payload,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        FraudEventMessage event = objectMapper.convertValue(payload, FraudEventMessage.class);

        log.debug(
                "Consumed fraud event from topic={}, partition={}, offset={}",
                topic,
                partition,
                offset);

        if (event.flagged()) {
            log.warn(
                    "FRAUD ALERT: transaction={}, account={}, score={}, reason={}",
                    event.transactionId(),
                    event.accountId(),
                    event.fraudScore(),
                    event.reason());

            Instant oneHourAgo = Instant.now().minusSeconds(3600);
            long flaggedCount =
                    fraudRecordRepository.countFlaggedByAccountIdSince(
                            event.accountId(), oneHourAgo);
            if (flaggedCount >= 3) {
                log.warn(
                        "HIGH RISK ACCOUNT: {} has {} flagged transactions "
                                + "in the last hour — consider account suspension",
                        event.accountId(),
                        flaggedCount);
            }
        } else {
            log.debug("Clean transaction confirmed: {}", event.transactionId());
        }

        acknowledgment.acknowledge();
    }
}
