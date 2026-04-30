package com.finflow.analytics.cache;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis cache for real-time analytics counters. Counters are incremented on every Kafka event so
 * the dashboard always shows current numbers without hitting the DB. TTL ensures stale counts
 * expire automatically. The AnalyticsReadModel (PostgreSQL) is the persistent source of truth --
 * Redis is an acceleration layer.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsRedisCache {

    private static final String DAILY_TX_COUNT = "analytics:daily:tx:count:";
    private static final String DAILY_TX_VOLUME = "analytics:daily:tx:volume:";
    private static final String DAILY_FRAUD_COUNT = "analytics:daily:fraud:count:";
    private static final String ACCOUNT_TX_COUNT = "analytics:account:tx:count:";

    private final RedisTemplate<String, Object> redisTemplate;

    public void incrementDailyTransactionCount(String date) {
        String key = DAILY_TX_COUNT + date;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 48, TimeUnit.HOURS);
        }
        log.debug("Daily TX count incremented for: {}", date);
    }

    public void incrementDailyVolume(String date, BigDecimal amount) {
        String key = DAILY_TX_VOLUME + date;
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        Double volume = redisTemplate.opsForValue().increment(key, safeAmount.doubleValue());
        if (volume != null && Double.compare(volume, safeAmount.doubleValue()) == 0) {
            redisTemplate.expire(key, 48, TimeUnit.HOURS);
        }
    }

    public void incrementDailyFraudCount(String date) {
        String key = DAILY_FRAUD_COUNT + date;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 48, TimeUnit.HOURS);
        }
    }

    public void incrementAccountTransactionCount(String accountId) {
        redisTemplate.opsForValue().increment(ACCOUNT_TX_COUNT + accountId);
    }

    public Long getDailyTransactionCount(String date) {
        Object value = redisTemplate.opsForValue().get(DAILY_TX_COUNT + date);
        if (value == null) {
            return 0L;
        }
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public Double getDailyVolume(String date) {
        Object value = redisTemplate.opsForValue().get(DAILY_TX_VOLUME + date);
        if (value == null) {
            return 0.0;
        }
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    public Long getDailyFraudCount(String date) {
        Object value = redisTemplate.opsForValue().get(DAILY_FRAUD_COUNT + date);
        if (value == null) {
            return 0L;
        }
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public void evictAccountCache(String accountId) {
        String accountPrefix = ACCOUNT_TX_COUNT + accountId;
        Set<String> accountKeys = redisTemplate.keys(accountPrefix + "*");
        if (accountKeys != null && !accountKeys.isEmpty()) {
            redisTemplate.delete(accountKeys);
        }
        redisTemplate.delete("analytics-summary::" + accountId);
        redisTemplate.delete("daily-metrics::" + accountId);
        log.debug("Cache evicted for account: {}", accountId);
    }
}
