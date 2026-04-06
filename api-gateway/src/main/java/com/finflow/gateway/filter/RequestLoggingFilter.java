package com.finflow.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Observability filter: logs each gateway request at arrival and again when the response is about to
 * be committed, including duration and correlation id. Skips {@code /actuator/health} to avoid noisy
 * logs. Does not log Authorization headers or bodies.
 * RequestLoggingFilter is a global filter in the API Gateway that runs for every incoming request and outgoing response.
   Its purpose is to consistently track each request by logging when it arrives, when the response is sent, how long the processing took,
   and attaching a correlation ID so all related logs can be easily linked and traced.
 *
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String HEALTH_PATH = "/actuator/health";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath(); // getRequest().getURI().getPath();
        if (HEALTH_PATH.equals(path)) {
            return chain.filter(exchange);
        }

        final long startMs = System.currentTimeMillis(); // Start Timer, Used later to calculate duration
        final String method = exchange.getRequest().getMethod().name(); // Extract Request Details : GET / POST / PUT etc.
        final String correlationId = resolveCorrelationId(exchange); // Get Correlation ID= Used to track request across services

        log.info("→ {} {} | correlationId={}", method, path, correlationId); // This is what logged here - GET /api/accounts | correlationId=abc123

        //This runs just before response is returned
        exchange.getResponse()
                .beforeCommit(
                        () -> {
                            long durationMs = System.currentTimeMillis() - startMs;
                            HttpStatusCode status = exchange.getResponse().getStatusCode();
                            String statusCode =
                                    status != null ? String.valueOf(status.value()) : "unknown";
                            log.info(
                                    "← {} {} | status={} | duration={}ms | correlationId={}", // log when going out
                                    method,
                                    path,
                                    statusCode,
                                    durationMs,
                                    correlationId);
                            return Mono.empty();
                        });

        return chain.filter(exchange); // continue filter chain
    }

    private static String resolveCorrelationId(ServerWebExchange exchange) {
        String raw = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
        if (raw == null || raw.isBlank()) {
            return "none";
        }
        return raw.trim();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
