package com.finflow.graphql.resolver;

import com.finflow.graphql.client.AnalyticsServiceClient;
import com.finflow.graphql.client.dto.AccountDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
@RequiredArgsConstructor
public class TransactionQueryResolver {

    private final AnalyticsServiceClient analyticsServiceClient;

    @QueryMapping
    public TransactionPageType transactionsByAccount(
            @Argument String accountId,
            @Argument Integer page,
            @Argument Integer size,
            @Argument String status) {
        int resolvedPage = page != null ? page : 0;
        int resolvedSize = size != null ? size : 20;
        log.debug(
                "GraphQL transactionsByAccount: accountId={}, page={}, size={}",
                accountId,
                resolvedPage,
                resolvedSize);

        // Placeholder: analytics-service currently exposes aggregated daily metrics,
        // not transaction-level rows for dashboard pagination.
        return new TransactionPageType(
                Collections.emptyList(), resolvedPage, resolvedSize, 0L, 0, true, true);
    }

    @SchemaMapping(typeName = "Transaction", field = "account")
    public CompletableFuture<AccountDto> resolveAccount(
            TransactionType transaction, DataLoader<String, AccountDto> accountDataLoader) {
        return accountDataLoader.load(transaction.accountId());
    }

    public record TransactionPageType(
            List<TransactionType> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last) {}

    public record TransactionType(
            String id,
            String accountId,
            BigDecimal amount,
            String currency,
            String status,
            String description,
            String initiatedBy,
            String paymentId,
            String failureReason,
            String correlationId,
            Instant createdAt,
            Instant completedAt,
            Instant updatedAt) {}
}
