package com.finflow.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.finflow.analytics.cache.AnalyticsRedisCache;
import com.finflow.analytics.consumer.AnalyticsEventConsumer;
import com.finflow.analytics.consumer.AnalyticsEventConsumer.FraudDetectedEvent;
import com.finflow.analytics.consumer.AnalyticsEventConsumer.TransactionKafkaEvent;
import com.finflow.analytics.service.AnalyticsAggregatorService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventConsumerTest {

    @Mock private AnalyticsAggregatorService aggregatorService;
    @Mock private AnalyticsRedisCache analyticsRedisCache;
    @Mock private Acknowledgment acknowledgment;

    @InjectMocks private AnalyticsEventConsumer consumer;

    @Test
    void consumeTransactionEvent_ProcessesAndAcknowledges() {
        TransactionKafkaEvent event =
                new TransactionKafkaEvent(
                        "evt-1",
                        "tx-1",
                        "acc-001",
                        BigDecimal.valueOf(100),
                        "USD",
                        "TRANSACTION_COMPLETED",
                        "COMPLETED",
                        "corr-1",
                        Instant.now());

        consumer.consumeTransactionEvent(event, "transactions", 0, 0L, acknowledgment);

        verify(aggregatorService).processTransactionEvent(event);
        verify(analyticsRedisCache).incrementDailyTransactionCount(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeTransactionEvent_DoesNotAcknowledge_WhenServiceThrows() {
        TransactionKafkaEvent event =
                new TransactionKafkaEvent(
                        "evt-1",
                        "tx-1",
                        "acc-001",
                        BigDecimal.valueOf(100),
                        "USD",
                        "TRANSACTION_COMPLETED",
                        "COMPLETED",
                        "corr-1",
                        Instant.now());
        doThrow(new RuntimeException("boom"))
                .when(aggregatorService)
                .processTransactionEvent(any());

        consumer.consumeTransactionEvent(event, "transactions", 0, 0L, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consumeFraudEvent_IncrementsFraudCounter_WhenFlagged() {
        FraudDetectedEvent event =
                new FraudDetectedEvent(
                        "f-1",
                        "tx-1",
                        "acc-001",
                        BigDecimal.valueOf(100),
                        "USD",
                        88,
                        "rule-hit",
                        "F-01",
                        true,
                        Instant.now());

        consumer.consumeFraudEvent(event, "fraud-events", acknowledgment);

        verify(aggregatorService).processFraudEvent(event);
        verify(analyticsRedisCache).incrementDailyFraudCount(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeFraudEvent_SkipsProcessing_WhenNotFlagged() {
        FraudDetectedEvent event =
                new FraudDetectedEvent(
                        "f-2",
                        "tx-2",
                        "acc-001",
                        BigDecimal.valueOf(100),
                        "USD",
                        22,
                        "low-risk",
                        "F-00",
                        false,
                        Instant.now());

        consumer.consumeFraudEvent(event, "fraud-events", acknowledgment);

        verify(aggregatorService, never()).processFraudEvent(any());
        verify(acknowledgment).acknowledge();
    }
}
