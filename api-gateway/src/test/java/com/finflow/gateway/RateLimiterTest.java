package com.finflow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9090/realms/finflow",
        "management.health.redis.enabled=false"
})
class RateLimiterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    @Qualifier("apiRateLimiter")
    private RedisRateLimiter apiRateLimiter;

    @Autowired
    @Qualifier("userKeyResolver")
    private KeyResolver userKeyResolver;

    @Test
    void testRateLimiterConfigBeanExists() throws Exception {
        assertNotNull(apiRateLimiter);

        Method getDefaultConfig = RedisRateLimiter.class.getDeclaredMethod("getDefaultConfig");
        getDefaultConfig.setAccessible(true);
        Object config = getDefaultConfig.invoke(apiRateLimiter);

        Method getReplenishRate = config.getClass().getMethod("getReplenishRate");
        int replenishRate = (int) getReplenishRate.invoke(config);
        assertEquals(100, replenishRate);
    }

    @Test
    void testKeyResolverBeanExists() {
        assertNotNull(userKeyResolver);
    }
}
