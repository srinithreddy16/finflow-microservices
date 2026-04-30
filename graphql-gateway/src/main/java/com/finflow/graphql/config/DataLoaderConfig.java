package com.finflow.graphql.config;

import com.finflow.graphql.dataloader.AccountDataLoader;
import com.finflow.graphql.dataloader.FraudScoreDataLoader;
import com.finflow.graphql.dataloader.TransactionDataLoader;
import graphql.GraphQLContext;
import lombok.RequiredArgsConstructor;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.DataLoaderRegistrar;

/**
 * Registers all DataLoader instances with the GraphQL execution engine.
 *
 * <p>DataLoaders are KEY to GraphQL performance. Without them:
 *
 * <p>- Fetching 20 transactions with their accounts = 21 DB calls (1+20) - This is the N+1
 * problem
 *
 * <p>With DataLoaders:
 *
 * <p>- Fetching 20 transactions with their accounts = 2 calls total - All 20 account IDs are
 * batched into one REST call to account-service
 *
 * <p>DataLoaders are per-request (not singletons) to prevent data leaking between requests. The
 * DataLoaderRegistry is request-scoped.
 *
 * <p>Caching within a request: if the same accountId appears in two different transactions in the
 * same query, the DataLoader returns the cached result from the first load -- not a second network
 * call.
 */
@Configuration
@RequiredArgsConstructor
public class DataLoaderConfig {

    private final AccountDataLoader accountDataLoader;
    private final TransactionDataLoader transactionDataLoader;
    private final FraudScoreDataLoader fraudScoreDataLoader;

    @Bean
    public DataLoaderRegistrar dataLoaderRegistrar() {
        return new DataLoaderRegistrar() {
            @Override
            public void registerDataLoaders(DataLoaderRegistry registry, GraphQLContext context) {
                DataLoaderOptions options =
                        DataLoaderOptions.newOptions()
                                .setBatchingEnabled(true)
                                .setCachingEnabled(true)
                                .setMaxBatchSize(100);

                registry.register(
                        AccountDataLoader.LOADER_NAME,
                        DataLoaderFactory.newDataLoader(
                                accountDataLoader.accountBatchLoader(), options));

                registry.register(
                        TransactionDataLoader.LOADER_NAME,
                        DataLoaderFactory.newDataLoader(
                                transactionDataLoader.transactionBatchLoader(), options));

                registry.register(
                        FraudScoreDataLoader.LOADER_NAME,
                        DataLoaderFactory.newDataLoader(
                                fraudScoreDataLoader.fraudScoreBatchLoader(), options));
            }
        };
    }
}
