package com.finflow.graphql.resolver;

import com.finflow.common.exception.FinFlowException;
import com.finflow.graphql.cache.GraphqlRedisCache;
import com.finflow.graphql.client.AnalyticsServiceClient;
import com.finflow.graphql.client.dto.AnalyticsSummaryDto;
import com.finflow.graphql.client.dto.DailyMetricsDto;
import com.finflow.graphql.client.dto.PlatformSummaryDto;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
@RequiredArgsConstructor
public class AnalyticsQueryResolver {

    private static final String DAILY_METRICS_KEY_PREFIX = "graphql:analytics:daily:";
    private static final List<String> PLATFORM_ALLOWED_ROLES =
            List.of("BUSINESS_ADMIN", "FINANCE_ANALYST");

    private final AnalyticsServiceClient analyticsServiceClient;
    private final GraphqlRedisCache graphqlRedisCache;

    @QueryMapping
    public Object analyticsSummary(
            @Argument String accountId,
            @Argument LocalDate fromDate,
            @Argument LocalDate toDate) {
        LocalDate resolvedFromDate = fromDate != null ? fromDate : LocalDate.now().minusDays(30);
        LocalDate resolvedToDate = toDate != null ? toDate : LocalDate.now();

        String fromText = resolvedFromDate.toString();
        String toText = resolvedToDate.toString();
        Object cached = graphqlRedisCache.getAnalyticsSummary(accountId, fromText, toText).orElse(null);
        if (cached != null) {
            log.debug(
                    "Cache hit: analytics summary for account {} ({} to {})",
                    accountId,
                    fromText,
                    toText);
            return cached;
        }

        AnalyticsSummaryDto summary =
                analyticsServiceClient.getSummary(accountId, resolvedFromDate, resolvedToDate);
        graphqlRedisCache.cacheAnalyticsSummary(accountId, fromText, toText, summary);
        log.info("Analytics summary fetched for account: {} ({} -> {})", accountId, fromText, toText);
        return summary;
    }

    @QueryMapping
    public Object dailyMetrics(@Argument String accountId, @Argument LocalDate date) {
        LocalDate resolvedDate = date != null ? date : LocalDate.now();
        String key = DAILY_METRICS_KEY_PREFIX + accountId + ":" + resolvedDate;
        Object cached = graphqlRedisCache.get(key, Object.class).orElse(null);
        if (cached != null) {
            return cached;
        }

        DailyMetricsDto metrics = analyticsServiceClient.getDailyMetrics(accountId, resolvedDate);
        graphqlRedisCache.set(key, metrics, java.time.Duration.ofMinutes(5));
        return metrics;
    }

    @QueryMapping
    public List<Object> accountHistory(@Argument String accountId, @Argument Integer days) {
        int resolvedDays = days != null ? days : 30;
        return analyticsServiceClient.getAccountHistory(accountId, resolvedDays).stream()
                .map(item -> (Object) item)
                .toList();
    }

    @QueryMapping
    public Object platformSummary(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = extractRoles(jwt);
        boolean allowed =
                roles.stream()
                        .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                        .anyMatch(PLATFORM_ALLOWED_ROLES::contains);
        if (!allowed) {
            throw FinFlowException.forbidden("Access denied");
        }

        Object cached = graphqlRedisCache.getPlatformSummary().orElse(null);
        if (cached != null) {
            return cached;
        }

        PlatformSummaryDto summary = analyticsServiceClient.getPlatformSummary();
        graphqlRedisCache.cachePlatformSummary(summary);
        return summary;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        if (jwt == null) {
            return Collections.emptyList();
        }

        List<String> direct = jwt.getClaimAsStringList("realm_access.roles");
        if (direct != null) {
            return direct;
        }

        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        return Collections.emptyList();
    }
}
