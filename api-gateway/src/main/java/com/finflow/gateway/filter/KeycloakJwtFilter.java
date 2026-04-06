package com.finflow.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway filter that validates the {@code Authorization: Bearer} JWT against Keycloak (via
 * {@link ReactiveJwtDecoder} configured from {@code spring.security.oauth2.resourceserver.jwt}).
 *
 * <p><b>Flow:</b>
 * <ol>
 *   <li>Read the {@code Authorization} header; reject with {@code 401} if missing, not prefixed with
 *       {@code Bearer }, or empty token.</li>
 *   <li>Decode and validate the JWT; on {@link JwtException}, respond with {@code 401} and an
 *       {@link ErrorResponse} body (token value is never logged).</li>
 *   <li>On success, enrich the downstream request with {@code X-User-Id} ({@code sub}),
 *       {@code X-User-Roles} (comma-separated {@code realm_access.roles}), and
 *       {@code X-Correlation-Id} (existing header or new UUID).</li>
 *   <li>Other reactive failures are handled via {@code onErrorResume}.</li>
 * </ol>
 */
@Slf4j
@Component
public class KeycloakJwtFilter extends AbstractGatewayFilterFactory<KeycloakJwtFilter.Config>
        implements GatewayFilterFactory<KeycloakJwtFilter.Config> {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    private final ReactiveJwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;

    public KeycloakJwtFilter(ReactiveJwtDecoder jwtDecoder, ObjectMapper objectMapper) {
        super(Config.class);
        this.jwtDecoder = jwtDecoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                return writeMissingAuthorization(exchange);
            }
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                return writeMissingAuthorization(exchange);
            }

            return jwtDecoder
                    .decode(token)
                    .flatMap(jwt -> forwardWithUserHeaders(exchange, chain, jwt))
                    .onErrorResume(JwtException.class, ex -> handleInvalidJwt(exchange, ex))
                    .onErrorResume(Exception.class, ex -> handleUnexpectedJwtError(exchange, ex));
        };
    }

    private Mono<Void> forwardWithUserHeaders(
            ServerWebExchange exchange, GatewayFilterChain chain, Jwt jwt) {
        String sub = jwt.getSubject() != null ? jwt.getSubject() : "";
        String rolesHeader = String.join(",", extractRealmRoles(jwt));
        String correlationId = resolveCorrelationId(exchange);

        ServerHttpRequest mutated = exchange.getRequest()
                .mutate()
                .header(HEADER_USER_ID, sub)
                .header(HEADER_USER_ROLES, rolesHeader)
                .header(HEADER_CORRELATION_ID, correlationId)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private static List<String> extractRealmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object rolesObj = map.get("roles");
        if (!(rolesObj instanceof List<?> raw)) {
            return List.of();
        }
        List<String> roles = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (o != null) {
                roles.add(o.toString());
            }
        }
        return roles;
    }

    private static String resolveCorrelationId(ServerWebExchange exchange) {
        String existing = exchange.getRequest().getHeaders().getFirst(HEADER_CORRELATION_ID);
        if (existing != null && !existing.isBlank()) {
            return existing.trim();
        }
        return UUID.randomUUID().toString();
    }

    private Mono<Void> writeMissingAuthorization(ServerWebExchange exchange) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("errorCode", "UNAUTHORIZED");
        body.put("message", "Missing or invalid Authorization header");
        return writeJson(exchange, HttpStatus.UNAUTHORIZED, body);
    }

    private Mono<Void> handleInvalidJwt(ServerWebExchange exchange, JwtException ex) {
        log.warn("JWT validation failed: {}", ex.getMessage());
        String path = exchange.getRequest().getURI().getPath();
        ErrorResponse response =
                ErrorResponse.of(ErrorCode.UNAUTHORIZED, "Invalid or expired token", path);
        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, response);
    }

    private Mono<Void> handleUnexpectedJwtError(ServerWebExchange exchange, Exception ex) {
        log.error("Unexpected error during JWT processing", ex);
        String path = exchange.getRequest().getURI().getPath();
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR, "Authentication processing failed", path);
        return writeErrorResponse(exchange, HttpStatus.INTERNAL_SERVER_ERROR, response);
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, Object body) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        return writeBytes(exchange, status, bytes);
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, ErrorResponse body) {
        return writeJson(exchange, status, body);
    }

    private Mono<Void> writeBytes(ServerWebExchange exchange, HttpStatus status, byte[] bytes) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /** Route configuration placeholder (no properties yet). */
    public static class Config {}
}
