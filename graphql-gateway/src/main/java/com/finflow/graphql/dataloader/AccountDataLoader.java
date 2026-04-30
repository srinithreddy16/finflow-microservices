package com.finflow.graphql.dataloader;

import com.finflow.graphql.client.AccountServiceClient;
import com.finflow.graphql.client.dto.AccountDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AccountDataLoader {

    public static final String LOADER_NAME = "accountDataLoader";

    private final AccountServiceClient accountServiceClient;

    public AccountDataLoader(AccountServiceClient accountServiceClient) {
        this.accountServiceClient = accountServiceClient;
    }

    public BatchLoaderWithContext<String, AccountDto> accountBatchLoader() {
        return (accountIds, batchLoaderEnvironment) -> loadAccounts(accountIds, batchLoaderEnvironment);
    }

    private CompletableFuture<List<AccountDto>> loadAccounts(
            List<String> accountIds, BatchLoaderEnvironment batchLoaderEnvironment) {
        log.debug("AccountDataLoader batching {} account lookups", accountIds.size());
        List<AccountDto> accounts = accountServiceClient.getAccountsByIds(new ArrayList<>(accountIds));
        Map<String, AccountDto> accountMap =
                accounts.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(AccountDto::id, Function.identity(), (a, b) -> a));

        List<AccountDto> results =
                accountIds.stream()
                        .map(id -> accountMap.getOrDefault(id, null))
                        .collect(Collectors.toList());
        return CompletableFuture.completedFuture(results);
    }
}
