package com.finflow.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finflow.common.dto.PagedResponse;
import com.finflow.transaction.command.CreateTransactionCommand;
import com.finflow.transaction.command.TransactionCommandHandler;
import com.finflow.transaction.event.TransactionCreatedEvent;
import com.finflow.transaction.dto.TransactionResponseDto;
import com.finflow.transaction.exception.GlobalExceptionHandler;
import com.finflow.transaction.exception.TransactionNotFoundException;
import com.finflow.transaction.mapper.TransactionMapper;
import com.finflow.transaction.controller.TransactionController;
import com.finflow.transaction.query.GetTransactionQuery;
import com.finflow.transaction.query.GetTransactionsByAccountQuery;
import com.finflow.transaction.query.TransactionQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TransactionController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TransactionCommandHandler transactionCommandHandler;

    @MockBean private TransactionQueryHandler transactionQueryHandler;

    @MockBean private TransactionMapper transactionMapper;

    @Test
    void createTransaction_Returns201_WhenValid() throws Exception {
        when(transactionCommandHandler.handle(any(CreateTransactionCommand.class)))
                .thenAnswer(
                        inv -> {
                            CreateTransactionCommand c = inv.getArgument(0);
                            return new TransactionCreatedEvent(
                                    c.transactionId(),
                                    c.accountId(),
                                    c.amount(),
                                    c.currency(),
                                    c.description(),
                                    c.initiatedBy(),
                                    c.correlationId(),
                                    1L);
                        });
        when(transactionQueryHandler.handle(any(GetTransactionQuery.class)))
                .thenAnswer(
                        inv -> {
                            GetTransactionQuery q = inv.getArgument(0);
                            return new TransactionResponseDto(
                                    q.transactionId(),
                                    "acc-001",
                                    new BigDecimal("500.00"),
                                    "USD",
                                    "PENDING",
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    Instant.now(),
                                    null,
                                    null,
                                    Instant.now());
                        });

        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"acc-001","amount":500.00,"currency":"USD"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createTransaction_Returns400_WhenAmountIsNegative() throws Exception {
        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"acc-001","amount":-100,"currency":"USD"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void createTransaction_Returns400_WhenCurrencyInvalid() throws Exception {
        mockMvc.perform(
                        post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"accountId":"acc-001","amount":100,"currency":"INVALID"}
                                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTransaction_Returns200_WhenFound() throws Exception {
        when(transactionQueryHandler.handle(any(GetTransactionQuery.class)))
                .thenReturn(
                        new TransactionResponseDto(
                                "tx-001",
                                "acc-001",
                                BigDecimal.TEN,
                                "USD",
                                "PENDING",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Instant.now(),
                                null,
                                null,
                                Instant.now()));

        mockMvc.perform(get("/api/transactions/tx-001")).andExpect(status().isOk());
    }

    @Test
    void getTransaction_Returns404_WhenNotFound() throws Exception {
        when(transactionQueryHandler.handle(any(GetTransactionQuery.class)))
                .thenThrow(new TransactionNotFoundException("unknown"));

        mockMvc.perform(get("/api/transactions/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void getByAccount_Returns200_WithPagedResult() throws Exception {
        TransactionResponseDto item =
                new TransactionResponseDto(
                        "t1",
                        "acc-001",
                        BigDecimal.ONE,
                        "USD",
                        "PENDING",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.now(),
                        null,
                        null,
                        Instant.now());
        when(transactionQueryHandler.handle(any(GetTransactionsByAccountQuery.class)))
                .thenReturn(
                        new PagedResponse<>(
                                List.of(item, item),
                                0,
                                10,
                                2L,
                                1,
                                true,
                                true));

        mockMvc.perform(get("/api/transactions/account/acc-001").queryParam("page", "0").queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}
