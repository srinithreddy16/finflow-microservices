package com.finflow.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.payment.exception.InsufficientBalanceException;
import com.finflow.payment.kafka.PaymentEventKafkaPublisher;
import com.finflow.payment.model.Payment;
import com.finflow.payment.model.PaymentStatus;
import com.finflow.payment.saga.PaymentEventPublisher;
import com.finflow.payment.saga.PaymentInitiatedMessage;
import com.finflow.payment.saga.PaymentSagaHandler;
import com.finflow.payment.service.PaymentService;
import com.rabbitmq.client.Channel;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentSagaHandlerTest {

    @Mock private PaymentService paymentService;
    @Mock private PaymentEventPublisher paymentEventPublisher;
    @Mock private PaymentEventKafkaPublisher paymentEventKafkaPublisher;
    @Mock private Channel channel;

    @InjectMocks private PaymentSagaHandler paymentSagaHandler;

    private PaymentInitiatedMessage message;

    @BeforeEach
    void setUp() throws Exception {
        message =
                new PaymentInitiatedMessage(
                        "tx-saga",
                        "acc-sender",
                        BigDecimal.valueOf(100),
                        "USD",
                        "corr-saga",
                        Instant.now());
        lenient().doNothing().when(channel).basicAck(anyLong(), anyBoolean());
        lenient().doNothing().when(channel).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void handlePaymentInitiated_PublishesCompleted_OnSuccess() throws Exception {
        Payment payment =
                Payment.builder()
                        .id("pay-1")
                        .transactionId("tx-saga")
                        .senderAccountId("acc-sender")
                        .receiverAccountId("FINFLOW-SYSTEM-ACCOUNT")
                        .amount(BigDecimal.valueOf(100))
                        .currency("USD")
                        .status(PaymentStatus.COMPLETED)
                        .correlationId("corr-saga")
                        .sagaId("tx-saga")
                        .build();
        when(paymentService.initiatePayment(
                        eq("tx-saga"),
                        eq("acc-sender"),
                        eq("FINFLOW-SYSTEM-ACCOUNT"),
                        eq(BigDecimal.valueOf(100)),
                        eq("USD"),
                        eq("corr-saga"),
                        eq("tx-saga")))
                .thenReturn(payment);

        paymentSagaHandler.handlePaymentInitiated(message, channel, 1L);

        verify(paymentEventPublisher)
                .publishCompleted(
                        eq("tx-saga"),
                        eq("pay-1"),
                        eq("corr-saga"),
                        eq(BigDecimal.valueOf(100)),
                        eq("USD"));
        verify(paymentEventKafkaPublisher).publishPaymentCompleted(same(payment), same(message));
        verify(channel).basicAck(1L, false);
    }

    @Test
    void handlePaymentInitiated_PublishesFailed_OnInsufficientBalance() throws Exception {
        when(paymentService.initiatePayment(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(BigDecimal.class),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenThrow(
                        new InsufficientBalanceException(
                                "acc-sender", BigDecimal.valueOf(100), BigDecimal.ZERO));

        paymentSagaHandler.handlePaymentInitiated(message, channel, 1L);

        verify(paymentEventPublisher)
                .publishFailed(
                        eq("tx-saga"),
                        eq("Insufficient balance"),
                        eq("INSUFFICIENT_BALANCE"),
                        eq("corr-saga"));
        verify(paymentEventKafkaPublisher)
                .publishPaymentFailed(same(message), eq("Insufficient balance"));
        verify(channel).basicAck(1L, false);
    }

    @Test
    void handlePaymentInitiated_Nacks_OnUnexpectedError() throws Exception {
        when(paymentService.initiatePayment(
                        anyString(),
                        anyString(),
                        anyString(),
                        any(BigDecimal.class),
                        anyString(),
                        anyString(),
                        anyString()))
                .thenThrow(new RuntimeException("boom"));

        paymentSagaHandler.handlePaymentInitiated(message, channel, 1L);

        verify(channel).basicNack(1L, false, false);
        verify(paymentEventPublisher, never())
                .publishFailed(anyString(), anyString(), anyString(), anyString());
    }
}
