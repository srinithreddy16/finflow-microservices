package com.finflow.transaction.query;

import com.finflow.common.dto.PagedResponse;
import com.finflow.transaction.config.RedisConfig;
import com.finflow.transaction.dto.TransactionResponseDto;
import com.finflow.transaction.dto.TransactionSummaryDto;
import com.finflow.transaction.exception.TransactionNotFoundException;
import com.finflow.transaction.mapper.TransactionMapper;
import com.finflow.transaction.model.Transaction;
import com.finflow.transaction.model.TransactionStatus;
import com.finflow.transaction.projection.ProjectionRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * The query handler is the read side of CQRS. It NEVER touches the EventStore or command objects.
 * It reads exclusively from the projection table which is maintained by {@link
 * com.finflow.transaction.projection.TransactionProjectionUpdater}. Caching is applied at this
 * layer to minimize DB load.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionQueryHandler {

    private final ProjectionRepository projectionRepository;
    private final TransactionMapper transactionMapper;

    @Cacheable(value = RedisConfig.CACHE_TRANSACTION, key = "#query.transactionId()")
    public TransactionResponseDto handle(GetTransactionQuery query) {
        Transaction transaction =
                projectionRepository
                        .findById(query.transactionId())
                        .orElseThrow(() -> new TransactionNotFoundException(query.transactionId()));
        log.debug("Transaction fetched from DB (or cache): {}", query.transactionId());
        return transactionMapper.toDto(transaction);
    }

    @Cacheable(
            value = RedisConfig.CACHE_TRANSACTIONS_BY_ACCOUNT,
            key =
                    "#query.accountId() + '-' + #query.page() + '-' + #query.size() + '-' + #query.status()")
    public PagedResponse<TransactionResponseDto> handle(GetTransactionsByAccountQuery query) {
        Sort sort =
                Sort.by(Sort.Direction.fromString(query.sortDirection()), query.sortBy());
        Pageable pageable = PageRequest.of(query.page(), query.size(), sort);

        String accountId = query.accountId();
        Page<Transaction> page =
                query.status() == null
                        ? projectionRepository.findByAccountId(accountId, pageable)
                        : projectionRepository.findByAccountIdAndStatus(
                                accountId, query.status(), pageable);

        PagedResponse<TransactionResponseDto> response =
                PagedResponse.from(page, transactionMapper.toDtoList(page.getContent()));
        log.debug(
                "Transactions fetched for account: {}, count: {}",
                accountId,
                page.getNumberOfElements());
        return response;
    }

    @Cacheable(value = RedisConfig.CACHE_TRANSACTION_SUMMARY, key = "#query.accountId()")
    public TransactionSummaryDto handle(GetTransactionSummaryQuery query) {
        String accountId = query.accountId();

        long totalTransactions = projectionRepository.countByAccountId(accountId);
        long completedTransactions =
                projectionRepository.countByAccountIdAndStatus(
                        accountId, TransactionStatus.COMPLETED);
        long failedTransactions =
                projectionRepository.countByAccountIdAndStatus(accountId, TransactionStatus.FAILED);
        long pendingTransactions =
                projectionRepository.countByAccountIdAndStatus(accountId, TransactionStatus.PENDING);
        long reversedTransactions =
                projectionRepository.countByAccountIdAndStatus(
                        accountId, TransactionStatus.REVERSED);
        BigDecimal totalCompletedAmount =
                projectionRepository.sumCompletedAmountByAccountId(accountId).orElse(BigDecimal.ZERO);

        String currency =
                projectionRepository
                        .findFirstByAccountIdOrderByCreatedAtDesc(accountId)
                        .map(Transaction::getCurrency)
                        .orElse(null);

        log.debug("Summary fetched for account: {}", accountId);
        return new TransactionSummaryDto(
                accountId,
                totalTransactions,
                completedTransactions,
                failedTransactions,
                pendingTransactions,
                reversedTransactions,
                totalCompletedAmount,
                currency);
    }

    @CacheEvict(
            value = {
                RedisConfig.CACHE_TRANSACTION,
                RedisConfig.CACHE_TRANSACTIONS_BY_ACCOUNT,
                RedisConfig.CACHE_TRANSACTION_SUMMARY
            },
            allEntries = true)
    public void evictCacheForAccount(String accountId) {
        log.debug("Cache evicted for account: {}", accountId);
    }
}
