package com.finflow.payment.controller;

import com.finflow.payment.dto.AccountBalanceDto;
import com.finflow.payment.dto.LedgerEntryDto;
import com.finflow.payment.dto.PaymentResponseDto;
import com.finflow.payment.mapper.PaymentMapper;
import com.finflow.payment.model.PaymentStatus;
import com.finflow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment controller exposing read endpoints for payments and ledger. WRITE operations (payment
 * initiation) happen exclusively through the RabbitMQ Choreography Saga — not via REST endpoints.
 * Clients should use transaction-service to initiate transactions, which in turn triggers the payment
 * saga.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID")
    public PaymentResponseDto getPaymentById(@PathVariable String id) {
        return paymentService.getPaymentById(id);
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "Get payment by transaction ID")
    public PaymentResponseDto getPaymentByTransactionId(@PathVariable String transactionId) {
        return paymentService.getPaymentByTransactionId(transactionId);
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get all payments for an account")
    public List<PaymentResponseDto> getPaymentsByAccount(
            @PathVariable String accountId,
            @RequestParam(required = false) PaymentStatus status) {
        return paymentService.getPaymentsByAccount(accountId, status);
    }

    @GetMapping("/account/{accountId}/balance")
    @Operation(summary = "Get account balance from ledger")
    public AccountBalanceDto getAccountBalance(@PathVariable String accountId) {
        return paymentService.getAccountBalance(accountId);
    }

    @GetMapping("/{id}/ledger")
    @Operation(summary = "Get ledger entries for a payment")
    public List<LedgerEntryDto> getLedgerEntriesForPayment(@PathVariable String id) {
        return paymentMapper.toLedgerDtoList(paymentService.listLedgerEntriesForPayment(id));
    }
}
