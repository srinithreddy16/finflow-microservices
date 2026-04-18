package com.finflow.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.transaction.event.TransactionCompletedEvent;
import com.finflow.transaction.event.TransactionCreatedEvent;
import com.finflow.transaction.event.TransactionFailedEvent;
import com.finflow.transaction.event.TransactionReversedEvent;
import com.finflow.transaction.model.Transaction;
import com.finflow.transaction.model.TransactionStatus;
import com.finflow.transaction.projection.ProjectionRepository;
import com.finflow.transaction.projection.TransactionProjectionUpdater;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionProjectionUpdaterTest {

    @Mock private ProjectionRepository projectionRepository;

    @InjectMocks private TransactionProjectionUpdater updater;

    @Test
    void onCreated_SavesNewProjection() {
        TransactionCreatedEvent event =
                new TransactionCreatedEvent(
                        "tx-001",
                        "acc-001",
                        BigDecimal.valueOf(500),
                        "USD",
                        "desc",
                        "user-001",
                        "corr-1",
                        1L);

        updater.onTransactionCreated(event);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(projectionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(saved.getCurrency()).isEqualTo("USD");
    }

    @Test
    void onCompleted_UpdatesExistingProjection() {
        Transaction pending =
                Transaction.builder()
                        .id("tx-001")
                        .accountId("acc-001")
                        .amount(BigDecimal.TEN)
                        .currency("USD")
                        .status(TransactionStatus.PENDING)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
        when(projectionRepository.findById("tx-001")).thenReturn(Optional.of(pending));

        TransactionCompletedEvent event =
                new TransactionCompletedEvent("tx-001", "pay-001", "corr", 2L);

        updater.onTransactionCompleted(event);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(projectionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(saved.getPaymentId()).isEqualTo("pay-001");
    }

    @Test
    void onCompleted_LogsWarn_WhenProjectionNotFound() {
        when(projectionRepository.findById("tx-001")).thenReturn(Optional.empty());
        TransactionCompletedEvent event =
                new TransactionCompletedEvent("tx-001", "pay-001", "corr", 2L);

        updater.onTransactionCompleted(event);

        verify(projectionRepository, never()).save(any());
    }

    @Test
    void onFailed_SetsFailureFields() {
        Transaction pending =
                Transaction.builder()
                        .id("tx-001")
                        .accountId("acc-001")
                        .amount(BigDecimal.TEN)
                        .currency("USD")
                        .status(TransactionStatus.PENDING)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
        when(projectionRepository.findById("tx-001")).thenReturn(Optional.of(pending));

        TransactionFailedEvent event =
                new TransactionFailedEvent(
                        "tx-001", "Fraud", "FRAUD_DETECTED", "corr", 2L);

        updater.onTransactionFailed(event);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(projectionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getFailureReason()).isEqualTo("Fraud");
        assertThat(saved.getFailureCode()).isEqualTo("FRAUD_DETECTED");
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void onReversed_SetsReversalFields() {
        Transaction completed =
                Transaction.builder()
                        .id("tx-001")
                        .accountId("acc-001")
                        .amount(BigDecimal.TEN)
                        .currency("USD")
                        .status(TransactionStatus.COMPLETED)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
        when(projectionRepository.findById("tx-001")).thenReturn(Optional.of(completed));

        TransactionReversedEvent event =
                new TransactionReversedEvent(
                        "tx-001", "Customer request", "user-001", "corr", 3L);

        updater.onTransactionReversed(event);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(projectionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(saved.getReversalReason()).isEqualTo("Customer request");
        assertThat(saved.getReversedBy()).isEqualTo("user-001");
    }
}
