package com.finflow.graphql;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.graphql.cache.GraphqlRedisCache;
import com.finflow.graphql.client.AccountServiceClient;
import com.finflow.graphql.client.AnalyticsServiceClient;
import com.finflow.graphql.client.ReportServiceClient;
import com.finflow.graphql.client.dto.AnalyticsSummaryDto;
import java.math.BigDecimal;
import java.time.Instant;
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
class TransactionQueryResolverTest {

    @MockBean private AnalyticsServiceClient analyticsServiceClient;
    @MockBean private ReportServiceClient reportServiceClient;
    @MockBean private AccountServiceClient accountServiceClient;
    @MockBean private GraphqlRedisCache graphqlRedisCache;

    @Autowired private GraphQlTester graphQlTester;

    @Test
    void transactionsByAccount_ReturnsPage_WhenValid() {
        graphQlTester
                .document(
                        """
                        query {
                          transactionsByAccount(accountId: "acc-001") {
                            content { id amount currency status }
                            totalElements
                            page
                            size
                          }
                        }
                        """)
                .execute()
                .path("transactionsByAccount.content")
                .entityList(Object.class)
                .hasSize(0);
    }

    @Test
    void analyticsSummary_ReturnsData_WhenCacheMiss() {
        when(graphqlRedisCache.getAnalyticsSummary(any(), any(), any())).thenReturn(Optional.empty());
        when(analyticsServiceClient.getSummary(any(), any(), any()))
                .thenReturn(
                        new AnalyticsSummaryDto(
                                "acc-001",
                                12,
                                10,
                                1,
                                1,
                                BigDecimal.valueOf(1000),
                                BigDecimal.valueOf(900),
                                1,
                                BigDecimal.valueOf(8.33),
                                BigDecimal.valueOf(83.33),
                                "USD",
                                LocalDate.now().minusDays(30),
                                LocalDate.now(),
                                Instant.now()));

        graphQlTester
                .document(
                        """
                        query {
                          analyticsSummary(accountId: "acc-001") {
                            totalTransactions
                          }
                        }
                        """)
                .execute()
                .path("analyticsSummary.totalTransactions")
                .hasValue();

        verify(analyticsServiceClient).getSummary(any(), any(), any());
    }

    @Test
    void analyticsSummary_ReturnsCachedData_WhenCacheHit() {
        when(graphqlRedisCache.getAnalyticsSummary(any(), any(), any()))
                .thenReturn(Optional.of(new Object()));

        graphQlTester
                .document(
                        """
                        query {
                          analyticsSummary(accountId: "acc-001") {
                            totalTransactions
                          }
                        }
                        """)
                .execute();

        verify(analyticsServiceClient, never()).getSummary(any(), any(), any());
    }
}
