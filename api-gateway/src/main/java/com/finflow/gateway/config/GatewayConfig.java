package com.finflow.gateway.config;

import com.finflow.gateway.filter.KeycloakJwtFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Central route table for FinFlow behind Spring Cloud Gateway (WebFlux).
 * <ul>
 *   <li><b>Auth</b> — {@code /auth/**}: public; rate-limited; path forwarded as-is to the auth service.</li>
 *   <li><b>Domain APIs</b> — {@code /api/...}: JWT handling via {@code keycloakJwtFilter}, Redis rate limiting,
 *       and (where configured) circuit breaker fallbacks to {@code /fallback/...}.</li>
 *   <li><b>Actuator</b> — {@code /actuator/**}: forwarded to this gateway instance (no extra filters).</li>
 * </ul>
 */
@Configuration
public class GatewayConfig {

    private final RouteLocatorBuilder routeLocatorBuilder;
    private final KeycloakJwtFilter keycloakJwtFilter;

    @Value("${service.auth.url:http://localhost:8082}")
    private String authServiceUrl;

    @Value("${service.account.url:http://localhost:8082}")
    private String accountServiceUrl;

    @Value("${service.transaction.url:http://localhost:8083}")
    private String transactionServiceUrl;

    @Value("${service.payment.url:http://localhost:8084}")
    private String paymentServiceUrl;

    @Value("${service.analytics.url:http://localhost:8088}")
    private String analyticsServiceUrl;

    @Value("${service.report.url:http://localhost:8089}")
    private String reportServiceUrl;

    @Value("${gateway.self.url:http://localhost:8080}")
    private String gatewaySelfUrl;

    public GatewayConfig(RouteLocatorBuilder routeLocatorBuilder, KeycloakJwtFilter keycloakJwtFilter) {
        this.routeLocatorBuilder = routeLocatorBuilder;
        this.keycloakJwtFilter = keycloakJwtFilter;
    }

    @Bean
    public RouteLocator finflowRouteLocator(
            @Qualifier("apiRateLimiter") RedisRateLimiter apiRateLimiter,
            @Qualifier("userKeyResolver") KeyResolver userKeyResolver) {
        return routeLocatorBuilder
                .routes()
                .route(
                        "auth-service-route",
                        r -> r.path("/auth/**")
                                .filters(
                                        f -> f.stripPrefix(0)
                                                .requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter)
                                                        .setKeyResolver(userKeyResolver)))
                                .uri(authServiceUrl))
                .route(
                        "account-service-route",
                        r -> r.path("/api/accounts/**")
                                .filters(
                                        f -> f.filter(keycloakJwtFilter.apply(new KeycloakJwtFilter.Config()))
                                                .requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter)
                                                        .setKeyResolver(userKeyResolver))
                                                .circuitBreaker(c -> c.setName("accountServiceCb")
                                                        .setFallbackUri("forward:/fallback/accounts")))
                                .uri(accountServiceUrl))
                .route(
                        "transaction-service-route",
                        r -> r.path("/api/transactions/**")
                                .filters(
                                        f -> f.filter(keycloakJwtFilter.apply(new KeycloakJwtFilter.Config()))
                                                .requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter)
                                                        .setKeyResolver(userKeyResolver))
                                                .circuitBreaker(c -> c.setName("transactionServiceCb")
                                                        .setFallbackUri("forward:/fallback/transactions")))
                                .uri(transactionServiceUrl))
                .route(
                        "payment-service-route",
                        r -> r.path("/api/payments/**")
                                .filters(
                                        f -> f.filter(keycloakJwtFilter.apply(new KeycloakJwtFilter.Config()))
                                                .requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter)
                                                        .setKeyResolver(userKeyResolver))
                                                .circuitBreaker(c -> c.setName("paymentServiceCb")
                                                        .setFallbackUri("forward:/fallback/payments")))
                                .uri(paymentServiceUrl))
                .route(
                        "analytics-service-route",
                        r -> r.path("/api/analytics/**")
                                .filters(
                                        f -> f.filter(keycloakJwtFilter.apply(new KeycloakJwtFilter.Config()))
                                                .requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter)
                                                        .setKeyResolver(userKeyResolver)))
                                .uri(analyticsServiceUrl))
                .route(
                        "report-service-route",
                        r -> r.path("/api/reports/**")
                                .filters(
                                        f -> f.filter(keycloakJwtFilter.apply(new KeycloakJwtFilter.Config()))
                                                .requestRateLimiter(c -> c.setRateLimiter(apiRateLimiter)
                                                        .setKeyResolver(userKeyResolver))
                                                .circuitBreaker(c -> c.setName("reportServiceCb")
                                                        .setFallbackUri("forward:/fallback/reports")))
                                .uri(reportServiceUrl))
                .route("actuator-route", r -> r.path("/actuator/**").uri(gatewaySelfUrl))
                .build();
    }
}
