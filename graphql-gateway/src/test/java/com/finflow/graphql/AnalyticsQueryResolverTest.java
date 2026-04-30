package com.finflow.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.finflow.graphql.cache.GraphqlRedisCache;
import com.finflow.graphql.client.AccountServiceClient;
import com.finflow.graphql.client.AnalyticsServiceClient;
import com.finflow.graphql.client.ReportServiceClient;
import com.finflow.graphql.client.dto.DailyMetricsDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.test.tester.GraphQlTester;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureGraphQlTester
class AnalyticsQueryResolverTest {

    @MockBean private AnalyticsServiceClient analyticsServiceClient;
    @MockBean private ReportServiceClient reportServiceClient;
    @MockBean private AccountServiceClient accountServiceClient;
    @MockBean private GraphqlRedisCache graphqlRedisCache;

    @Autowired private GraphQlTester graphQlTester;

    @Test
    void dailyMetrics_ReturnsMetrics_ForToday() {
        when(graphqlRedisCache.get(any(), any())).thenReturn(Optional.empty());
        when(analyticsServiceClient.getDailyMetrics(any(), any()))
                .thenReturn(
                        new DailyMetricsDto(
                                LocalDate.now(),
                                5,
                                4,
                                1,
                                BigDecimal.valueOf(500),
                                BigDecimal.valueOf(400),
                                0,
                                "USD"));

        graphQlTester
                .document(
                        """
                        query {
                          dailyMetrics(accountId: "acc-001") {
                            date
                            totalTransactions
                            completedTransactions
                            totalVolume
                            currency
                          }
                        }
                        """)
                .execute()
                .path("dailyMetrics")
                .hasValue();
    }

    @Test
    void platformSummary_ReturnsForbidden_WhenNotAdmin() {
        graphQlTester
                .document("query { platformSummary { totalTransactionsToday } }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }
}
