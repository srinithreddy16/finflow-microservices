package com.finflow.transaction.config;

import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configures Redis for the Transaction Service query side.
 *
 * <p>Frequently queried transactions are cached so read requests do not always hit the database.
 *
 * <p><strong>Strategy:</strong> Read-through cache for the CQRS query side. The cache is filled on
 * first read and expires by TTL. The write path does not invalidate cache directly — eventual
 * consistency via TTL expiry is acceptable for analytics. For real-time balance checks, bypass the
 * cache with a direct database query.
 */
@Configuration
@EnableCaching
@Profile("!itest")
public class RedisConfig {

    public static final String CACHE_TRANSACTION = "transactions";
    public static final String CACHE_TRANSACTIONS_BY_ACCOUNT = "transactionsByAccount";
    public static final String CACHE_TRANSACTION_SUMMARY = "transactionSummary";

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        RedisSerializationContext.SerializationPair<Object> valuePair =
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer);

        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
                        .serializeValuesWith(valuePair)
                        .disableCachingNullValues();

        RedisCacheConfiguration transactionsByAccountConfig =
                defaultConfig.entryTtl(Duration.ofMinutes(2));
        RedisCacheConfiguration transactionSummaryConfig =
                defaultConfig.entryTtl(Duration.ofMinutes(1));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(CACHE_TRANSACTION, defaultConfig)
                .withCacheConfiguration(CACHE_TRANSACTIONS_BY_ACCOUNT, transactionsByAccountConfig)
                .withCacheConfiguration(CACHE_TRANSACTION_SUMMARY, transactionSummaryConfig)
                .build();
    }
}
