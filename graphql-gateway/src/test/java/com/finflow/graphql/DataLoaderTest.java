package com.finflow.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.graphql.client.AccountServiceClient;
import com.finflow.graphql.client.dto.AccountDto;
import com.finflow.graphql.dataloader.AccountDataLoader;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class DataLoaderTest {

    @MockBean private AccountServiceClient accountServiceClient;

    @Autowired private AccountDataLoader accountDataLoader;

    @Test
    void accountDataLoader_BatchesMultipleLookups() throws ExecutionException, InterruptedException {
        when(accountServiceClient.getAccountsByIds(anyList()))
                .thenReturn(
                        List.of(
                                new AccountDto(
                                        "acc-1",
                                        "a1@finflow.com",
                                        "A1",
                                        "User",
                                        null,
                                        "ACTIVE",
                                        "tenant-1",
                                        true,
                                        Instant.now(),
                                        Instant.now()),
                                new AccountDto(
                                        "acc-2",
                                        "a2@finflow.com",
                                        "A2",
                                        "User",
                                        null,
                                        "ACTIVE",
                                        "tenant-1",
                                        true,
                                        Instant.now(),
                                        Instant.now()),
                                new AccountDto(
                                        "acc-3",
                                        "a3@finflow.com",
                                        "A3",
                                        "User",
                                        null,
                                        "ACTIVE",
                                        "tenant-1",
                                        true,
                                        Instant.now(),
                                        Instant.now())));

        List<AccountDto> results =
                accountDataLoader
                        .accountBatchLoader()
                        .load(List.of("acc-1", "acc-2", "acc-3"), null)
                        .toCompletableFuture()
                        .get();

        assertThat(results).hasSize(3);
        verify(accountServiceClient, times(1)).getAccountsByIds(anyList());
    }

    @Test
    void accountDataLoader_ReturnsNull_WhenAccountNotFound()
            throws ExecutionException, InterruptedException {
        when(accountServiceClient.getAccountsByIds(anyList()))
                .thenReturn(
                        List.of(
                                new AccountDto(
                                        "acc-1",
                                        "a1@finflow.com",
                                        "A1",
                                        "User",
                                        null,
                                        "ACTIVE",
                                        "tenant-1",
                                        true,
                                        Instant.now(),
                                        Instant.now())));

        List<AccountDto> results =
                accountDataLoader
                        .accountBatchLoader()
                        .load(List.of("acc-1", "missing"), null)
                        .toCompletableFuture()
                        .get();

        assertThat(results).hasSize(2);
        assertThat(results.get(1)).isNull();
    }
}
