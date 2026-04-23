package com.finflow.payment.service;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.payment.dto.AccountBalanceDto;
import com.finflow.payment.dto.PaymentResponseDto;
import com.finflow.payment.exception.InsufficientBalanceException;
import com.finflow.payment.exception.PaymentNotFoundException;
import com.finflow.payment.mapper.PaymentMapper;
import com.finflow.payment.model.LedgerEntry;
import com.finflow.payment.model.Payment;
import com.finflow.payment.model.PaymentStatus;
import com.finflow.payment.repository.LedgerRepository;
import com.finflow.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LedgerRepository ledgerRepository;
    private final LedgerService ledgerService;
    private final PaymentMapper paymentMapper;

    @Transactional
    public Payment initiatePayment(
            String transactionId,
            String senderAccountId,
            String receiverAccountId,
            BigDecimal amount,
            String currency,
            String correlationId,
            String sagaId) {

        if (paymentRepository.existsByTransactionId(transactionId)) {
            log.warn(
                    "Payment already exists for transaction: {} — returning existing",
                    transactionId);
            return paymentRepository.findByTransactionId(transactionId).get();
        }

        Payment payment =
                Payment.builder()
                        .transactionId(transactionId)
                        .senderAccountId(senderAccountId)
                        .receiverAccountId(receiverAccountId)
                        .amount(amount)
                        .currency(currency)
                        .status(PaymentStatus.PENDING)
                        .correlationId(correlationId)
                        .sagaId(sagaId)
                        .build();
        payment = paymentRepository.save(payment);

        payment.setStatus(PaymentStatus.PROCESSING);
        payment = paymentRepository.save(payment);

        try {
            ledgerService.createDoubleEntry(
                    payment.getId(),
                    senderAccountId,
                    receiverAccountId != null ? receiverAccountId : "EXTERNAL",
                    amount,
                    currency,
                    "Transaction " + transactionId);

            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(Instant.now());
            payment = paymentRepository.save(payment);

            log.info(
                    "Payment {} completed for transaction {} amount {} {}",
                    payment.getId(),
                    transactionId,
                    amount,
                    currency);

            return payment;
        } catch (InsufficientBalanceException e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            log.warn("Payment failed - insufficient balance: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Internal error: " + e.getMessage());
            paymentRepository.save(payment);
            log.error("Payment processing error for transaction: {}", transactionId, e);
            throw FinFlowException.internalError(
                    ErrorCode.INTERNAL_SERVER_ERROR, "Payment processing failed", e);
        }
    }

    public PaymentResponseDto getPaymentById(String paymentId) {
        Payment payment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return paymentMapper.toDto(payment);
    }

    public PaymentResponseDto getPaymentByTransactionId(String transactionId) {
        Payment payment =
                paymentRepository
                        .findByTransactionId(transactionId)
                        .orElseThrow(
                                () -> new PaymentNotFoundException("transactionId", transactionId));
        return paymentMapper.toDto(payment);
    }

    public List<PaymentResponseDto> getPaymentsByAccount(String accountId, PaymentStatus status) {
        List<Payment> payments =
                status == null
                        ? paymentRepository.findBySenderAccountIdOrderByCreatedAtDesc(accountId)
                        : paymentRepository.findBySenderAccountIdAndStatusOrderByCreatedAtDesc(
                                accountId, status);
        return paymentMapper.toDtoList(payments);
    }

    /**
     * Ledger rows for a payment (caller maps to DTOs). Verifies the payment exists.
     */
    public List<LedgerEntry> listLedgerEntriesForPayment(String paymentId) {
        paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return ledgerRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    public AccountBalanceDto getAccountBalance(String accountId) {
        BigDecimal balance = ledgerService.getAccountBalance(accountId);
        long totalTransactions = paymentRepository.countBySenderAccountId(accountId);
        long completedTransactions =
                paymentRepository.countBySenderAccountIdAndStatus(
                        accountId, PaymentStatus.COMPLETED);
        return new AccountBalanceDto(
                accountId,
                balance,
                "USD",
                totalTransactions,
                completedTransactions,
                Instant.now());
    }
}
