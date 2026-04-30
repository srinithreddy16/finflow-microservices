package com.finflow.analytics.config;

import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfig {

    public static final String CACHE_ANALYTICS_SUMMARY = "analytics-summary";
    public static final String CACHE_DAILY_METRICS = "daily-metrics";
    public static final String CACHE_PLATFORM_SUMMARY = "platform-summary";

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer();

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisSerializationContext.SerializationPair<Object> valuePair =
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer());

        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(2))
                        .serializeValuesWith(valuePair)
                        .disableCachingNullValues();

        RedisCacheConfiguration analyticsSummaryConfig = defaultConfig.entryTtl(Duration.ofMinutes(1));
        RedisCacheConfiguration dailyMetricsConfig = defaultConfig.entryTtl(Duration.ofMinutes(5));
        RedisCacheConfiguration platformSummaryConfig = defaultConfig.entryTtl(Duration.ofSeconds(30));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(CACHE_ANALYTICS_SUMMARY, analyticsSummaryConfig)
                .withCacheConfiguration(CACHE_DAILY_METRICS, dailyMetricsConfig)
                .withCacheConfiguration(CACHE_PLATFORM_SUMMARY, platformSummaryConfig)
                .build();
    }
}
