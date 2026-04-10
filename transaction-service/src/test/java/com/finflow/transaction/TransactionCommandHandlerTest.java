package com.finflow.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.proto.fraud.FraudCheckProto.FraudCheckResponse;
import com.finflow.transaction.command.CreateTransactionCommand;
import com.finflow.transaction.command.TransactionCommandHandler;
import com.finflow.transaction.event.EventStore;
import com.finflow.transaction.event.TransactionCreatedEvent;
import com.finflow.transaction.grpc.FraudCheckGrpcClient;
import com.finflow.transaction.kafka.TransactionEventPublisher;
import com.finflow.transaction.saga.PaymentSagaInitiator;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionCommandHandlerTest {

    @Mock private EventStore eventStore;

    @Mock private FraudCheckGrpcClient fraudCheckGrpcClient;

    @Mock private TransactionEventPublisher transactionEventPublisher;

    @Mock private PaymentSagaInitiator paymentSagaInitiator;

    @InjectMocks private TransactionCommandHandler handler;

    private CreateTransactionCommand createCommand;

    @BeforeEach
    void setUp() {
        createCommand =
                new CreateTransactionCommand(
                        "tx-001",
                        "acc-001",
                        BigDecimal.valueOf(500),
                        "USD",
                        "Test payment",
                        "user-001",
                        "corr-001");
    }

    @Test
    void handleCreate_ShouldCreateTransaction_WhenFraudCheckPasses() {
        when(eventStore.findByAggregateId("tx-001")).thenReturn(Collections.emptyList());
        when(fraudCheckGrpcClient.checkTransaction(
                        anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(
                        FraudCheckResponse.newBuilder().setFlagged(false).setFraudScore(0).build());

        TransactionCreatedEvent result = handler.handle(createCommand);

        assertThat(result).isNotNull();
        verify(eventStore, atLeastOnce()).append(any());
        verify(transactionEventPublisher).publishCreated(any());
        verify(paymentSagaInitiator).initiatePayment(any());
    }

    @Test
    void handleCreate_ShouldThrowFraudException_WhenFraudDetected() {
        when(eventStore.findByAggregateId("tx-001")).thenReturn(Collections.emptyList());
        when(fraudCheckGrpcClient.checkTransaction(
                        anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(
                        FraudCheckResponse.newBuilder()
                                .setFlagged(true)
                                .setReason("Suspicious amount")
                                .build());

        FinFlowException ex =
                assertThrows(FinFlowException.class, () -> handler.handle(createCommand));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TRANSACTION_FLAGGED_AS_FRAUD);
        verify(eventStore, atLeastOnce()).append(any());
        verify(transactionEventPublisher).publishFailed(any());
        verify(transactionEventPublisher, never()).publishCreated(any());
        verify(paymentSagaInitiator, never()).initiatePayment(any());
    }

    @Test
    void handleCreate_ShouldThrowException_WhenTransactionAlreadyExists() {
        when(eventStore.findByAggregateId("tx-001"))
                .thenReturn(List.of(Mockito.mock(TransactionCreatedEvent.class)));

        FinFlowException ex =
                assertThrows(FinFlowException.class, () -> handler.handle(createCommand));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TRANSACTION_ALREADY_EXISTS);
        verify(eventStore, never()).append(any());
        verify(paymentSagaInitiator, never()).initiatePayment(any());
    }
}
