package com.finflow.graphql.cache;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis cache layer for GraphQL resolver results.
 *
 * <p>Cache-aside pattern: 1. Resolver checks cache first 2. On cache miss: calls downstream REST
 * service 3. Caches the result with TTL 4. Returns result
 *
 * <p>Cache failure strategy: FAIL OPEN If Redis is unavailable, resolvers fall back to direct REST
 * calls. GraphQL responses are never blocked by cache failures.
 *
 * <p>TTL strategy:
 *
 * <p>- Platform summary: 30s (changes every transaction) - Analytics summary: 2min (aggregated --
 * changes less often) - Account data: 5min (changes infrequently) - Reports: 1min (status changes
 * as reports generate)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GraphqlRedisCache {

    private static final String ANALYTICS_SUMMARY_KEY = "graphql:analytics:summary:";
    private static final String DAILY_METRICS_KEY = "graphql:analytics:daily:";
    private static final String PLATFORM_SUMMARY_KEY = "graphql:platform:summary";
    private static final String ACCOUNT_KEY = "graphql:account:";
    private static final String REPORT_KEY = "graphql:report:";

    private final RedisTemplate<String, Object> redisTemplate;

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            if (type.isInstance(value)) {
                return Optional.of(type.cast(value));
            }
            return Optional.empty();
        } catch (DataAccessException | ClassCastException ex) {
            log.warn("Redis get failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (DataAccessException ex) {
            log.warn("Redis set failed: {}", ex.getMessage());
        }
    }

    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException ex) {
            log.warn("Redis evict failed: {}", ex.getMessage());
        }
    }

    public Optional<Object> getAnalyticsSummary(String accountId, String fromDate, String toDate) {
        String key = ANALYTICS_SUMMARY_KEY + accountId + ":" + fromDate + ":" + toDate;
        return get(key, Object.class);
    }

    public void cacheAnalyticsSummary(String accountId, String fromDate, String toDate, Object summary) {
        String key = ANALYTICS_SUMMARY_KEY + accountId + ":" + fromDate + ":" + toDate;
        set(key, summary, Duration.ofMinutes(2));
        log.debug("Analytics summary cached for account: {}", accountId);
    }

    public Optional<Object> getPlatformSummary() {
        return get(PLATFORM_SUMMARY_KEY, Object.class);
    }

    public void cachePlatformSummary(Object summary) {
        set(PLATFORM_SUMMARY_KEY, summary, Duration.ofSeconds(30));
    }

    public Optional<Object> getAccount(String accountId) {
        return get(ACCOUNT_KEY + accountId, Object.class);
    }

    public void cacheAccount(String accountId, Object account) {
        set(ACCOUNT_KEY + accountId, account, Duration.ofMinutes(5));
    }

    public Optional<Object> getReport(String reportId) {
        return get(REPORT_KEY + reportId, Object.class);
    }

    public void cacheReport(String reportId, Object report) {
        set(REPORT_KEY + reportId, report, Duration.ofMinutes(1));
    }
}
