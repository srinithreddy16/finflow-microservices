package com.finflow.graphql.dataloader;

import com.finflow.graphql.client.AccountServiceClient;
import com.finflow.graphql.client.dto.TransactionDto;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.BatchLoader;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransactionDataLoader {

    public static final String LOADER_NAME = "transactionDataLoader";

    private final AccountServiceClient accountServiceClient;

    public TransactionDataLoader(AccountServiceClient accountServiceClient) {
        this.accountServiceClient = accountServiceClient;
    }

    public BatchLoader<String, TransactionDto> transactionBatchLoader() {
        return transactionIds -> {
            log.debug("TransactionDataLoader: {} IDs requested", transactionIds.size());
            List<TransactionDto> results = Collections.nCopies(transactionIds.size(), null);
            return CompletableFuture.completedFuture(results);
        };
    }
}
