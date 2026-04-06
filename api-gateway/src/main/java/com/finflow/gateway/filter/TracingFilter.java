package com.finflow.gateway.filter;

import com.finflow.common.util.IdGenerator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Ensures every gateway request carries stable identifiers for end-to-end observability.
 *
 * <p>A <b>correlation ID</b> ties together log lines and events for a single logical request as it
 * crosses the gateway and downstream microservices; without it, matching scattered log entries in
 * systems like Jaeger or your log aggregator is impractical at scale. The <b>trace ID</b> aligns with
 * distributed tracing (e.g. OpenTelemetry/Jaeger) so spans can be linked across service
 * boundaries. This filter runs first so downstream route filters, security, and backends all see the
 * same headers on the forwarded request, while the client receives the same IDs on the HTTP
 * response for support and debugging.
 * This filter does:
  1.Ensures every request has IDs
  2.Generates IDs if missing
  3.Adds IDs to:
     Request (for services)
     Response (for client)
  4. Runs first in pipeline
 Correlation ID = Order ID
 Trace ID = Shipment tracking
 */

//This TracingFilter Assigns a unique tracking ID to every request so it can be traced across all microservices.

@Component
public class TracingFilter implements GlobalFilter, Ordered {

    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = resolveCorrelationId(exchange);
        String traceId = resolveTraceId(exchange);

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header(HEADER_CORRELATION_ID, correlationId)
                .header(HEADER_TRACE_ID, traceId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        mutatedExchange.getResponse().getHeaders().add(HEADER_CORRELATION_ID, correlationId);
        mutatedExchange.getResponse().getHeaders().add(HEADER_TRACE_ID, traceId);

        return chain.filter(mutatedExchange);
    }

    private static String resolveCorrelationId(ServerWebExchange exchange) {
        String existing = exchange.getRequest().getHeaders().getFirst(HEADER_CORRELATION_ID);
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        return IdGenerator.correlationId();
    }

    private static String resolveTraceId(ServerWebExchange exchange) {
        String existing = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID);
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        return IdGenerator.generate();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
