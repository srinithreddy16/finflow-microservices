package com.finflow.analytics.service;

import com.finflow.analytics.consumer.AnalyticsEventConsumer;
import com.finflow.analytics.model.AnalyticsReadModel;
import com.finflow.analytics.repository.AnalyticsReadModelRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsAggregatorService {

    private final AnalyticsReadModelRepository repository;

    @Transactional
    public void processTransactionEvent(AnalyticsEventConsumer.TransactionKafkaEvent event) {
        LocalDate today =
                LocalDate.ofInstant(
                        event.occurredOn() != null ? event.occurredOn() : Instant.now(),
                        ZoneOffset.UTC);
        String currency = event.currency() != null ? event.currency() : "USD";

        AnalyticsReadModel model =
                repository.findByAccountIdAndDateAndCurrency(event.accountId(), today, currency)
                        .orElseGet(
                                () ->
                                        AnalyticsReadModel.builder()
                                                .accountId(event.accountId())
                                                .date(today)
                                                .currency(currency)
                                                .totalTransactions(0L)
                                                .completedTransactions(0L)
                                                .failedTransactions(0L)
                                                .reversedTransactions(0L)
                                                .fraudFlags(0L)
                                                .totalVolume(BigDecimal.ZERO)
                                                .completedVolume(BigDecimal.ZERO)
                                                .avgTransactionAmount(BigDecimal.ZERO)
                                                .build());

        if (event.amount() != null) {
            model.incrementTransaction(event.amount(), event.status());
        } else {
            model.incrementTransaction(BigDecimal.ZERO, event.status());
        }

        repository.save(model);
        log.debug("Analytics model updated for account: {}, date: {}", event.accountId(), today);
    }

    @Transactional
    public void processFraudEvent(AnalyticsEventConsumer.FraudDetectedEvent event) {
        LocalDate today =
                LocalDate.ofInstant(
                        event.occurredOn() != null ? event.occurredOn() : Instant.now(),
                        ZoneOffset.UTC);
        String currency = event.currency() != null ? event.currency() : "USD";

        AnalyticsReadModel model =
                repository.findByAccountIdAndDateAndCurrency(event.accountId(), today, currency)
                        .orElseGet(
                                () ->
                                        AnalyticsReadModel.builder()
                                                .accountId(event.accountId())
                                                .date(today)
                                                .currency(currency)
                                                .totalTransactions(0L)
                                                .completedTransactions(0L)
                                                .failedTransactions(0L)
                                                .reversedTransactions(0L)
                                                .fraudFlags(0L)
                                                .totalVolume(BigDecimal.ZERO)
                                                .completedVolume(BigDecimal.ZERO)
                                                .avgTransactionAmount(BigDecimal.ZERO)
                                                .build());

        model.setFraudFlags(model.getFraudFlags() + 1);
        repository.save(model);
        log.info("Fraud flag recorded in analytics for account: {}", event.accountId());
    }
}
