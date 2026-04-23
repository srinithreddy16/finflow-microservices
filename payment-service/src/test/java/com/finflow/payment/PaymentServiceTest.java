package com.finflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.payment.exception.InsufficientBalanceException;
import com.finflow.payment.mapper.PaymentMapper;
import com.finflow.payment.model.Payment;
import com.finflow.payment.model.PaymentStatus;
import com.finflow.payment.repository.LedgerRepository;
import com.finflow.payment.repository.PaymentRepository;
import com.finflow.payment.service.LedgerService;
import com.finflow.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private LedgerRepository ledgerRepository;
    @Mock private LedgerService ledgerService;
    @Mock private PaymentMapper paymentMapper;

    @InjectMocks private PaymentService paymentService;

    @Test
    void initiatePayment_Success_WhenAllConditionsMet() {
        when(paymentRepository.existsByTransactionId("tx-001")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(
                        inv -> {
                            Payment p = inv.getArgument(0);
                            if (p.getId() == null) {
                                p.setId("pay-generated");
                            }
                            return p;
                        });
        when(ledgerService.createDoubleEntry(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        Payment result =
                paymentService.initiatePayment(
                        "tx-001",
                        "sender-001",
                        "receiver-001",
                        BigDecimal.valueOf(500),
                        "USD",
                        "corr-001",
                        "saga-001");

        verify(paymentRepository, times(3)).save(any(Payment.class));
        verify(ledgerService, times(1))
                .createDoubleEntry(
                        "pay-generated",
                        "sender-001",
                        "receiver-001",
                        BigDecimal.valueOf(500),
                        "USD",
                        "Transaction tx-001");
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getId()).isEqualTo("pay-generated");
    }

    @Test
    void initiatePayment_ReturnsExisting_WhenDuplicate() {
        Payment existing =
                Payment.builder()
                        .id("existing-pay")
                        .transactionId("tx-dup")
                        .senderAccountId("sender-001")
                        .receiverAccountId("receiver-001")
                        .amount(BigDecimal.valueOf(100))
                        .currency("USD")
                        .status(PaymentStatus.COMPLETED)
                        .correlationId("corr")
                        .sagaId("saga")
                        .build();
        when(paymentRepository.existsByTransactionId("tx-dup")).thenReturn(true);
        when(paymentRepository.findByTransactionId("tx-dup")).thenReturn(Optional.of(existing));

        Payment result =
                paymentService.initiatePayment(
                        "tx-dup",
                        "sender-001",
                        "receiver-001",
                        BigDecimal.valueOf(500),
                        "USD",
                        "corr-001",
                        "saga-001");

        verify(ledgerService, never()).createDoubleEntry(any(), any(), any(), any(), any(), any());
        assertThat(result).isSameAs(existing);
    }

    @Test
    void initiatePayment_SetsFailedStatus_WhenInsufficientBalance() {
        when(paymentRepository.existsByTransactionId("tx-002")).thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(
                        inv -> {
                            Payment p = inv.getArgument(0);
                            if (p.getId() == null) {
                                p.setId("pay-fail");
                            }
                            return p;
                        });
        when(ledgerService.createDoubleEntry(any(), any(), any(), any(), any(), any()))
                .thenThrow(
                        new InsufficientBalanceException(
                                "sender-001", BigDecimal.valueOf(500), BigDecimal.ZERO));

        assertThatThrownBy(
                        () ->
                                paymentService.initiatePayment(
                                        "tx-002",
                                        "sender-001",
                                        "receiver-001",
                                        BigDecimal.valueOf(500),
                                        "USD",
                                        "corr-001",
                                        "saga-001"))
                .isInstanceOf(InsufficientBalanceException.class);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(3)).save(captor.capture());
        Payment lastSave = captor.getAllValues().get(2);
        assertThat(lastSave.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(lastSave.getFailureReason()).isNotBlank();
    }
}
