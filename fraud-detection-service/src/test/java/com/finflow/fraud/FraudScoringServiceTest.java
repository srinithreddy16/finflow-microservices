package com.finflow.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.fraud.kafka.FraudEventPublisher;
import com.finflow.fraud.model.FraudCheckRequest;
import com.finflow.fraud.model.FraudRecord;
import com.finflow.fraud.model.FraudScore;
import com.finflow.fraud.repository.FraudRecordRepository;
import com.finflow.fraud.rules.FraudRuleEngine;
import com.finflow.fraud.service.FraudScoringService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FraudScoringServiceTest {

    @Mock private FraudRuleEngine fraudRuleEngine;
    @Mock private FraudRecordRepository fraudRecordRepository;
    @Mock private FraudEventPublisher fraudEventPublisher;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private FraudScoringService fraudScoringService;

    @Test
    void evaluateAndPersist_SavesRecordAndReturnsScore() throws Exception {
        FraudCheckRequest request =
                new FraudCheckRequest("tx-001", "acc-001", new BigDecimal("100"), "USD", "US");
        when(fraudRecordRepository.findByTransactionId("tx-001")).thenReturn(Optional.empty());
        when(fraudRuleEngine.evaluate(request)).thenReturn(FraudScore.clean("tx-001", "acc-001"));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        FraudScore result = fraudScoringService.evaluateAndPersist(request, "SYNC_GRPC");

        verify(fraudRecordRepository).save(any(FraudRecord.class));
        verify(fraudEventPublisher, never()).publishFraudDetected(any(), any());
        assertThat(result.flagged()).isFalse();
    }

    @Test
    void evaluateAndPersist_PublishesEvent_WhenFlagged() throws Exception {
        FraudCheckRequest request =
                new FraudCheckRequest("tx-002", "acc-001", new BigDecimal("100"), "USD", "US");
        FraudScore flagged =
                FraudScore.flagged(
                        "tx-002",
                        "acc-001",
                        80,
                        "suspicious",
                        "AMOUNT_THRESHOLD",
                        List.of("AMOUNT_THRESHOLD"));
        when(fraudRecordRepository.findByTransactionId("tx-002")).thenReturn(Optional.empty());
        when(fraudRuleEngine.evaluate(request)).thenReturn(flagged);
        when(objectMapper.writeValueAsString(any())).thenReturn("[\"AMOUNT_THRESHOLD\"]");

        fraudScoringService.evaluateAndPersist(request, "SYNC_GRPC");

        verify(fraudEventPublisher).publishFraudDetected(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void evaluateAndPersist_ReturnsCached_WhenTransactionAlreadyChecked() throws Exception {
        FraudCheckRequest request =
                new FraudCheckRequest("tx-003", "acc-001", new BigDecimal("100"), "USD", "US");
        FraudRecord existing =
                FraudRecord.builder()
                        .transactionId("tx-003")
                        .accountId("acc-001")
                        .fraudScore(10)
                        .flagged(false)
                        .reason("cached")
                        .failureCode("CLEAN")
                        .triggeredRules("[]")
                        .amount(new BigDecimal("100"))
                        .currency("USD")
                        .countryCode("US")
                        .checkType("SYNC_GRPC")
                        .evaluatedAt(Instant.parse("2024-01-01T12:00:00Z"))
                        .build();
        when(fraudRecordRepository.findByTransactionId("tx-003")).thenReturn(Optional.of(existing));
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Collections.emptyList());

        FraudScore result = fraudScoringService.evaluateAndPersist(request, "SYNC_GRPC");

        verify(fraudRuleEngine, never()).evaluate(any());
        verify(fraudRecordRepository, never()).save(any());
        assertThat(result.transactionId()).isEqualTo("tx-003");
        assertThat(result.accountId()).isEqualTo("acc-001");
    }
}
