package com.finflow.analytics.consumer;

import com.finflow.analytics.cache.AnalyticsRedisCache;
import com.finflow.analytics.service.AnalyticsAggregatorService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for analytics-service.
 *
 * <p>Topics consumed:
 *
 * <p>- transactions: all transaction lifecycle events (CREATED, COMPLETED, etc.) - analytics:
 * payment analytics events from payment-service - fraud-events: fraud detection results from
 * fraud-detection-service
 *
 * <p>Each consumed event:
 *
 * <p>1. Updates the PostgreSQL AnalyticsReadModel (persistent aggregation) 2. Increments Redis
 * counters (real-time counters)
 *
 * <p>The two-layer approach ensures both fast reads (Redis) and durable storage (PostgreSQL) for
 * analytics data.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsEventConsumer {

    private final AnalyticsAggregatorService analyticsAggregatorService;
    private final AnalyticsRedisCache analyticsRedisCache;

    public record TransactionKafkaEvent(
            String eventId,
            String transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            String eventType,
            String status,
            String correlationId,
            Instant occurredOn) {}

    public record FraudDetectedEvent(
            String eventId,
            String transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            int fraudScore,
            String reason,
            String failureCode,
            boolean flagged,
            Instant occurredOn) {}

    @KafkaListener(
            topics = {"transactions", "analytics"},
            groupId = "analytics-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeTransactionEvent(
            TransactionKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        log.debug(
                "Analytics event received: topic={}, type={}, account={}, amount={}",
                topic,
                event != null ? event.eventType() : null,
                event != null ? event.accountId() : null,
                event != null ? event.amount() : null);

        try {
            analyticsAggregatorService.processTransactionEvent(event);

            String today = LocalDate.now().toString();
            analyticsRedisCache.incrementDailyTransactionCount(today);
            if (event != null && event.amount() != null) {
                analyticsRedisCache.incrementDailyVolume(today, event.amount());
            }
            if (event != null && event.accountId() != null) {
                analyticsRedisCache.incrementAccountTransactionCount(event.accountId());
            }

            acknowledgment.acknowledge();
            log.debug("Transaction event processed and acknowledged");
        } catch (Exception ex) {
            log.error("Failed to process transaction event: {}", ex.getMessage(), ex);
        }
    }

    @KafkaListener(
            topics = "fraud-events",
            groupId = "analytics-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeFraudEvent(
            FraudDetectedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        log.debug(
                "Fraud event received: transactionId={}, flagged={}, score={}",
                event != null ? event.transactionId() : null,
                event != null && event.flagged(),
                event != null ? event.fraudScore() : 0);

        try {
            if (event != null && event.flagged()) {
                analyticsAggregatorService.processFraudEvent(event);
                String today = LocalDate.now().toString();
                analyticsRedisCache.incrementDailyFraudCount(today);
                log.info(
                        "Fraud flag recorded for account: {}, score: {}",
                        event.accountId(),
                        event.fraudScore());
            }

            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process fraud event: {}", ex.getMessage(), ex);
        }
    }
}
