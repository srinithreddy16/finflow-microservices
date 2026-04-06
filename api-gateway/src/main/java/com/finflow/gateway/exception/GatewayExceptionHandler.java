package com.finflow.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global reactive exception handler for API Gateway.
 *
 * <p>Maps known exceptions to stable {@link ErrorResponse} payloads and writes JSON directly to the
 * reactive HTTP response before Spring's default error handler executes.</p>
 * ErrorWebExceptionHandler- handles all exceptions in WebFlux
 *
 */
@Slf4j
@Component
@Order(-1) // runs before default Spring error handler. So your custom logic takes priority.
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status;
        ErrorResponse body;
        String path = exchange.getRequest().getURI().getPath();

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            String message = (rse.getReason() == null || rse.getReason().isBlank())
                    ? status.getReasonPhrase()
                    : rse.getReason();
            body = ErrorResponse.of(mapErrorCode(status), message, path);
        } else if (ex instanceof JwtValidationException) {
            status = HttpStatus.UNAUTHORIZED;
            body = ErrorResponse.of(ErrorCode.UNAUTHORIZED, "Invalid or expired token", path);
        } else if (ex instanceof CallNotPermittedException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            body = ErrorResponse.of(
                    ErrorCode.SERVICE_UNAVAILABLE, "Service temporarily unavailable", path);
        } else if (ex instanceof IllegalArgumentException iae) {
            status = HttpStatus.BAD_REQUEST;
            String message = (iae.getMessage() == null || iae.getMessage().isBlank())
                    ? "Invalid request"
                    : iae.getMessage();
            body = ErrorResponse.of(ErrorCode.VALIDATION_FAILED, message, path);
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            body = ErrorResponse.of(
                    ErrorCode.INTERNAL_SERVER_ERROR, "An unexpected error occurred", path);
            log.error("Unhandled gateway exception", ex);
        }

        byte[] bytes = toJsonBytes(body);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse()
                .writeWith(Mono.just(buffer))
                .doOnError(writeError -> DataBufferUtils.release(buffer));
    }

    private byte[] toJsonBytes(ErrorResponse body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return "{\"errorCode\":\"INTERNAL_SERVER_ERROR\",\"message\":\"An unexpected error occurred\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static ErrorCode mapErrorCode(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return ErrorCode.UNAUTHORIZED;
        }
        if (status == HttpStatus.FORBIDDEN) {
            return ErrorCode.FORBIDDEN;
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return ErrorCode.VALIDATION_FAILED;
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }
        if (status.is4xxClientError()) {
            return ErrorCode.VALIDATION_FAILED;
        }
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }
}
