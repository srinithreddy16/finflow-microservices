package com.finflow.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.analytics.consumer.AnalyticsEventConsumer.FraudDetectedEvent;
import com.finflow.analytics.consumer.AnalyticsEventConsumer.TransactionKafkaEvent;
import com.finflow.analytics.model.AnalyticsReadModel;
import com.finflow.analytics.repository.AnalyticsReadModelRepository;
import com.finflow.analytics.service.AnalyticsAggregatorService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsAggregatorServiceTest {

    @Mock private AnalyticsReadModelRepository repository;

    @InjectMocks private AnalyticsAggregatorService aggregatorService;

    @Test
    void processTransactionEvent_CreatesNewModel_WhenFirstEventForDay() {
        when(repository.findByAccountIdAndDateAndCurrency(any(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(AnalyticsReadModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionKafkaEvent event =
                new TransactionKafkaEvent(
                        "evt-1",
                        "tx-1",
                        "acc-001",
                        BigDecimal.valueOf(500),
                        "USD",
                        "TRANSACTION_COMPLETED",
                        "COMPLETED",
                        "corr-1",
                        Instant.now());

        aggregatorService.processTransactionEvent(event);

        ArgumentCaptor<AnalyticsReadModel> captor = ArgumentCaptor.forClass(AnalyticsReadModel.class);
        verify(repository).save(captor.capture());
        AnalyticsReadModel saved = captor.getValue();
        assertThat(saved.getTotalTransactions()).isEqualTo(1);
        assertThat(saved.getCompletedTransactions()).isEqualTo(1);
        assertThat(saved.getTotalVolume()).isEqualByComparingTo("500");
    }

    @Test
    void processTransactionEvent_UpdatesExisting_WhenModelExists() {
        AnalyticsReadModel existing =
                AnalyticsReadModel.builder()
                        .accountId("acc-001")
                        .date(LocalDate.now(ZoneOffset.UTC))
                        .currency("USD")
                        .totalTransactions(5)
                        .completedTransactions(2)
                        .failedTransactions(1)
                        .reversedTransactions(0)
                        .fraudFlags(0)
                        .totalVolume(BigDecimal.valueOf(1000))
                        .completedVolume(BigDecimal.valueOf(700))
                        .avgTransactionAmount(BigDecimal.valueOf(200))
                        .build();
        when(repository.findByAccountIdAndDateAndCurrency(any(), any(), any()))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(AnalyticsReadModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionKafkaEvent event =
                new TransactionKafkaEvent(
                        "evt-2",
                        "tx-2",
                        "acc-001",
                        BigDecimal.valueOf(100),
                        "USD",
                        "TRANSACTION_COMPLETED",
                        "COMPLETED",
                        "corr-2",
                        Instant.now());

        aggregatorService.processTransactionEvent(event);

        assertThat(existing.getTotalTransactions()).isEqualTo(6);
    }

    @Test
    void processTransactionEvent_IncrementsFailed_WhenStatusFailed() {
        when(repository.findByAccountIdAndDateAndCurrency(any(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(AnalyticsReadModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionKafkaEvent event =
                new TransactionKafkaEvent(
                        "evt-3",
                        "tx-3",
                        "acc-001",
                        BigDecimal.valueOf(50),
                        "USD",
                        "TRANSACTION_FAILED",
                        "FAILED",
                        "corr-3",
                        Instant.now());

        aggregatorService.processTransactionEvent(event);

        ArgumentCaptor<AnalyticsReadModel> captor = ArgumentCaptor.forClass(AnalyticsReadModel.class);
        verify(repository).save(captor.capture());
        AnalyticsReadModel saved = captor.getValue();
        assertThat(saved.getFailedTransactions()).isEqualTo(1);
        assertThat(saved.getCompletedTransactions()).isEqualTo(0);
    }

    @Test
    void processFraudEvent_IncrementsFraudFlags() {
        AnalyticsReadModel model =
                AnalyticsReadModel.builder()
                        .accountId("acc-001")
                        .date(LocalDate.now(ZoneOffset.UTC))
                        .currency("USD")
                        .fraudFlags(2)
                        .totalVolume(BigDecimal.ZERO)
                        .completedVolume(BigDecimal.ZERO)
                        .avgTransactionAmount(BigDecimal.ZERO)
                        .build();
        when(repository.findByAccountIdAndDateAndCurrency(any(), any(), any()))
                .thenReturn(Optional.of(model));
        when(repository.save(any(AnalyticsReadModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FraudDetectedEvent event =
                new FraudDetectedEvent(
                        "fraud-1",
                        "tx-9",
                        "acc-001",
                        BigDecimal.valueOf(250),
                        "USD",
                        92,
                        "high-risk pattern",
                        "FRD-001",
                        true,
                        Instant.now());

        aggregatorService.processFraudEvent(event);

        assertThat(model.getFraudFlags()).isEqualTo(3);
    }
}
