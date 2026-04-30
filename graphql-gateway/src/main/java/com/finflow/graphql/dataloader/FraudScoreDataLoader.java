package com.finflow.graphql.dataloader;

import com.finflow.graphql.client.AnalyticsServiceClient;
import com.finflow.graphql.client.dto.FraudScoreDto;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.BatchLoader;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FraudScoreDataLoader {

    public static final String LOADER_NAME = "fraudScoreDataLoader";

    private final AnalyticsServiceClient analyticsServiceClient;

    public FraudScoreDataLoader(AnalyticsServiceClient analyticsServiceClient) {
        this.analyticsServiceClient = analyticsServiceClient;
    }

    public BatchLoader<String, FraudScoreDto> fraudScoreBatchLoader() {
        return transactionIds -> {
            log.debug("FraudScoreDataLoader: {} transaction IDs", transactionIds.size());
            List<FraudScoreDto> results = Collections.nCopies(transactionIds.size(), null);
            return CompletableFuture.completedFuture(results);
        };
    }
}
