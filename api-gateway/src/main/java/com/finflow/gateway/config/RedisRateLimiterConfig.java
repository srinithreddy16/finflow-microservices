package com.finflow.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;

/**
 * Per-user Redis-backed rate limiting for the gateway ({@link RedisRateLimiter} + {@link KeyResolver}).
 * {@link org.springframework.data.redis.connection.RedisConnectionFactory} is provided by Spring Boot from
 * {@code application.yml} — not declared here.
 * This file RedisRateLimiterConfig.java responsible for:
 * Limiting how many requests a user can make
 * Using Redis to track request counts
 * Identifying users based on JWT (userId) or IP address
 */
@Configuration
public class RedisRateLimiterConfig {

    /**
     * Token bucket: 100 tokens/s replenish, burst 200, 1 token per request.
     * User can make 100 requests per second
     * Can temporarily spike up to 200
     * Each request consumes 1 token
     */
    @Bean(name = "apiRateLimiter")
    public RedisRateLimiter apiRateLimiter() {
        return new RedisRateLimiter(100, 200, 1);
    }

    /**
     * Rate limiting key: JWT subject (userId) or IP for unauthenticated requests
     * Who are we rate limiting
     */
    @Bean(name = "userKeyResolver")
    public KeyResolver userKeyResolver() {
        // Rate limiting key: JWT subject (userId) or IP for unauthenticated requests
        return exchange -> jwtSubjectOrIp(exchange);
    }

    private static Mono<String> jwtSubjectOrIp(ServerWebExchange exchange) {
        return exchange
                .getPrincipal() // Gets logged-in user (from Spring Security)
                .flatMap(principal -> subjectFromJwtPrincipal(principal)) //Extract userId (sub) from JWT
                .switchIfEmpty(Mono.defer(() -> clientIp(exchange))); // If no JWT → use IP address
    }

    /**
     * Resolves {@code sub} from the JWT carried on {@link ServerWebExchange#getPrincipal()}.
     * Extracting User ID from JWT
     */
    private static Mono<String> subjectFromJwtPrincipal(Principal principal) {
        Object claimSource = principal;
        if (principal instanceof Authentication authentication) {
            claimSource = authentication.getPrincipal();
        }
        if (claimSource instanceof Jwt jwt) {
            return Mono.justOrEmpty(jwt.getSubject());
        }
        if (claimSource instanceof OAuth2AuthenticatedPrincipal oauth2) {
            Object sub = oauth2.getAttribute("sub");
            return sub != null ? Mono.just(sub.toString()) : Mono.empty();
        }
        return Mono.empty();
    }


    //FallBack- If user is not logged in: Use IP address: If no IP:-> anonymous
    private static Mono<String> clientIp(ServerWebExchange exchange) {
        if (exchange.getRequest().getRemoteAddress() == null
                || exchange.getRequest().getRemoteAddress().getAddress() == null) {
            return Mono.just("anonymous");
        }
        return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }
}
