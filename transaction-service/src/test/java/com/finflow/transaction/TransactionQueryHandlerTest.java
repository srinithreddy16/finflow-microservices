package com.finflow.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.common.dto.PagedResponse;
import com.finflow.transaction.dto.TransactionResponseDto;
import com.finflow.transaction.dto.TransactionSummaryDto;
import com.finflow.transaction.exception.TransactionNotFoundException;
import com.finflow.transaction.mapper.TransactionMapper;
import com.finflow.transaction.model.Transaction;
import com.finflow.transaction.model.TransactionStatus;
import com.finflow.transaction.projection.ProjectionRepository;
import com.finflow.transaction.query.GetTransactionQuery;
import com.finflow.transaction.query.GetTransactionSummaryQuery;
import com.finflow.transaction.query.GetTransactionsByAccountQuery;
import com.finflow.transaction.query.TransactionQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TransactionQueryHandlerTest {

    @Mock private ProjectionRepository projectionRepository;

    @Mock private TransactionMapper transactionMapper;

    @InjectMocks private TransactionQueryHandler queryHandler;

    @Test
    void handleGetTransaction_ReturnsDto_WhenFound() {
        Transaction transaction = new Transaction();
        transaction.setId("tx-001");
        when(projectionRepository.findById("tx-001")).thenReturn(Optional.of(transaction));
        TransactionResponseDto dto =
                new TransactionResponseDto(
                        "tx-001",
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
        when(transactionMapper.toDto(transaction)).thenReturn(dto);

        TransactionResponseDto result =
                queryHandler.handle(new GetTransactionQuery("tx-001", "user-001"));

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("tx-001");
    }

    @Test
    void handleGetTransaction_ThrowsNotFoundException_WhenMissing() {
        when(projectionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(
                TransactionNotFoundException.class,
                () -> queryHandler.handle(new GetTransactionQuery("missing", "user")));
    }

    @Test
    void handleGetByAccount_ReturnsPaged_WhenNoStatusFilter() {
        List<Transaction> txs = List.of(new Transaction(), new Transaction(), new Transaction());
        Page<Transaction> page = new PageImpl<>(txs, PageRequest.of(0, 20), 3);
        when(projectionRepository.findByAccountId(eq("acc-001"), any(Pageable.class)))
                .thenReturn(page);

        List<TransactionResponseDto> dtos =
                List.of(
                        sampleDto("1"),
                        sampleDto("2"),
                        sampleDto("3"));
        when(transactionMapper.toDtoList(txs)).thenReturn(dtos);

        PagedResponse<TransactionResponseDto> result =
                queryHandler.handle(GetTransactionsByAccountQuery.of("acc-001", "user"));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(3L);
    }

    @Test
    void handleGetByAccount_FiltersByStatus_WhenStatusProvided() {
        List<Transaction> txs = List.of(new Transaction());
        Page<Transaction> page = new PageImpl<>(txs, PageRequest.of(0, 20), 1);
        when(projectionRepository.findByAccountIdAndStatus(
                        eq("acc-001"), eq(TransactionStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(page);
        when(transactionMapper.toDtoList(txs)).thenReturn(List.of(sampleDto("1")));

        GetTransactionsByAccountQuery query =
                new GetTransactionsByAccountQuery(
                        "acc-001",
                        TransactionStatus.COMPLETED,
                        0,
                        20,
                        "createdAt",
                        "DESC",
                        "user");

        queryHandler.handle(query);

        verify(projectionRepository).findByAccountIdAndStatus(
                eq("acc-001"), eq(TransactionStatus.COMPLETED), any(Pageable.class));
        verify(projectionRepository, never()).findByAccountId(any(), any(Pageable.class));
    }

    @Test
    void handleGetSummary_ReturnsSummary_WithCorrectCounts() {
        String accountId = "acc-001";
        when(projectionRepository.countByAccountId(accountId)).thenReturn(10L);
        when(projectionRepository.countByAccountIdAndStatus(accountId, TransactionStatus.COMPLETED))
                .thenReturn(3L);
        when(projectionRepository.countByAccountIdAndStatus(accountId, TransactionStatus.FAILED))
                .thenReturn(2L);
        when(projectionRepository.countByAccountIdAndStatus(accountId, TransactionStatus.PENDING))
                .thenReturn(4L);
        when(projectionRepository.countByAccountIdAndStatus(accountId, TransactionStatus.REVERSED))
                .thenReturn(1L);
        when(projectionRepository.sumCompletedAmountByAccountId(accountId))
                .thenReturn(Optional.of(BigDecimal.valueOf(1500)));
        when(projectionRepository.findFirstByAccountIdOrderByCreatedAtDesc(accountId))
                .thenReturn(Optional.empty());

        TransactionSummaryDto summary =
                queryHandler.handle(new GetTransactionSummaryQuery(accountId, "user"));

        assertThat(summary.totalCompletedAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
        assertThat(summary.totalTransactions()).isEqualTo(10L);
        assertThat(summary.completedTransactions()).isEqualTo(3L);
        assertThat(summary.failedTransactions()).isEqualTo(2L);
        assertThat(summary.pendingTransactions()).isEqualTo(4L);
        assertThat(summary.reversedTransactions()).isEqualTo(1L);
    }

    private static TransactionResponseDto sampleDto(String id) {
        return new TransactionResponseDto(
                id,
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
    }
}
