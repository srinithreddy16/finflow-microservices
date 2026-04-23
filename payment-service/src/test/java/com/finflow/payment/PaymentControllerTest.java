package com.finflow.payment;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finflow.payment.controller.PaymentController;
import com.finflow.payment.dto.AccountBalanceDto;
import com.finflow.payment.dto.PaymentResponseDto;
import com.finflow.payment.exception.GlobalExceptionHandler;
import com.finflow.payment.exception.PaymentNotFoundException;
import com.finflow.payment.mapper.PaymentMapper;
import com.finflow.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PaymentService paymentService;
    @MockBean private PaymentMapper paymentMapper;

    @Test
    void getPaymentById_Returns200_WhenFound() throws Exception {
        PaymentResponseDto dto =
                new PaymentResponseDto(
                        "pay-001",
                        "tx-001",
                        "sender-1",
                        "recv-1",
                        BigDecimal.valueOf(100),
                        "USD",
                        "COMPLETED",
                        null,
                        "corr",
                        Instant.parse("2024-01-01T00:00:00Z"),
                        Instant.parse("2024-01-01T00:01:00Z"),
                        Instant.parse("2024-01-01T00:01:00Z"));
        when(paymentService.getPaymentById("pay-001")).thenReturn(dto);

        mockMvc.perform(get("/api/payments/pay-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pay-001"))
                .andExpect(jsonPath("$.transactionId").value("tx-001"));
    }

    @Test
    void getPaymentById_Returns404_WhenNotFound() throws Exception {
        when(paymentService.getPaymentById("unknown"))
                .thenThrow(new PaymentNotFoundException("unknown"));

        mockMvc.perform(get("/api/payments/unknown")).andExpect(status().isNotFound());
    }

    @Test
    void getAccountBalance_Returns200_WithBalance() throws Exception {
        AccountBalanceDto dto =
                new AccountBalanceDto(
                        "acc-001",
                        BigDecimal.valueOf(2500),
                        "USD",
                        5L,
                        4L,
                        Instant.now());
        when(paymentService.getAccountBalance("acc-001")).thenReturn(dto);

        mockMvc.perform(get("/api/payments/account/acc-001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-001"))
                .andExpect(jsonPath("$.balance").value(2500));
    }
}
