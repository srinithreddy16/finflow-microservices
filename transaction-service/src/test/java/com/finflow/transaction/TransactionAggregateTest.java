package com.finflow.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.finflow.transaction.aggregate.TransactionAggregate;
import com.finflow.transaction.event.TransactionCompletedEvent;
import com.finflow.transaction.event.TransactionCreatedEvent;
import com.finflow.transaction.event.TransactionEvent;
import com.finflow.transaction.event.TransactionFailedEvent;
import com.finflow.transaction.model.TransactionStatus;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionAggregateTest {

    @Test
    void create_ShouldRaiseTransactionCreatedEvent() {
        TransactionAggregate aggregate =
                TransactionAggregate.create(
                        "tx-001",
                        "acc-001",
                        BigDecimal.valueOf(500),
                        "USD",
                        "Test payment",
                        "user-001",
                        "corr-001");

        assertThat(aggregate.getUncommittedEvents()).hasSize(1);
        assertThat(aggregate.getUncommittedEvents().getFirst())
                .isInstanceOf(TransactionCreatedEvent.class);
        assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(aggregate.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void create_ShouldThrowException_WhenAmountIsZero() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TransactionAggregate.create(
                                "tx-001",
                                "acc-001",
                                BigDecimal.ZERO,
                                "USD",
                                "Test",
                                "user-001",
                                "corr-001"));
    }

    @Test
    void create_ShouldThrowException_WhenAmountIsNegative() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TransactionAggregate.create(
                                "tx-001",
                                "acc-001",
                                BigDecimal.valueOf(-100),
                                "USD",
                                "Test",
                                "user-001",
                                "corr-001"));
    }

    @Test
    void complete_ShouldRaiseCompletedEvent() {
        TransactionAggregate aggregate =
                TransactionAggregate.create(
                        "tx-001",
                        "acc-001",
                        BigDecimal.valueOf(100),
                        "USD",
                        "Test",
                        "user-001",
                        "corr-001");
        aggregate.markEventsAsCommitted();

        aggregate.complete("pay-001", "corr-001");

        assertThat(aggregate.getUncommittedEvents()).hasSize(1);
        assertThat(aggregate.getUncommittedEvents().getFirst())
                .isInstanceOf(TransactionCompletedEvent.class);
        assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void complete_ShouldThrow_WhenNotPending() {
        TransactionAggregate aggregate =
                TransactionAggregate.create(
                        "tx-001",
                        "acc-001",
                        BigDecimal.valueOf(100),
                        "USD",
                        "Test",
                        "user-001",
                        "corr-001");
        aggregate.markEventsAsCommitted();
        aggregate.complete("pay-001", "corr-001");
        aggregate.markEventsAsCommitted();

        assertThrows(IllegalStateException.class, () -> aggregate.complete("pay-002", "corr-002"));
    }

    @Test
    void fail_ShouldRaiseFailedEvent() {
        TransactionAggregate aggregate =
                TransactionAggregate.create(
                        "tx-001",
                        "acc-001",
                        BigDecimal.valueOf(100),
                        "USD",
                        "Test",
                        "user-001",
                        "corr-001");

        aggregate.fail("Fraud detected", "FRAUD_DETECTED", "corr-001");

        assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(aggregate.getUncommittedEvents().getLast())
                .isInstanceOf(TransactionFailedEvent.class);
    }

    @Test
    void reverse_ShouldThrow_WhenStatusIsPending() {
        TransactionAggregate aggregate =
                TransactionAggregate.create(
                        "tx-001",
                        "acc-001",
                        BigDecimal.valueOf(100),
                        "USD",
                        "Test",
                        "user-001",
                        "corr-001");

        assertThrows(
                IllegalStateException.class,
                () -> aggregate.reverse("reason", "user-x", "corr-001"));
    }

    @Test
    void reconstitute_ShouldRestoreState_FromEvents() {
        List<TransactionEvent> events =
                List.of(
                        new TransactionCreatedEvent(
                                "tx-001",
                                "acc-001",
                                BigDecimal.valueOf(250),
                                "EUR",
                                "Desc",
                                "user-001",
                                "corr-001",
                                1L),
                        new TransactionCompletedEvent("tx-001", "pay-001", "corr-001", 2L));

        TransactionAggregate aggregate = TransactionAggregate.reconstitute(events);

        assertThat(aggregate.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(aggregate.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(250));
    }

    @Test
    void reconstitute_ShouldThrow_WhenEventsEmpty() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransactionAggregate.reconstitute(Collections.emptyList()));
    }
}
