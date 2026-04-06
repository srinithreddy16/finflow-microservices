package com.finflow.gateway.fallback;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Circuit-breaker fallback endpoints used by gateway routes when downstream services are
 * unavailable. Each endpoint returns a reactive 503 response with a standard FinFlow
 * {@link ErrorResponse} payload so clients get a stable error contract.
 */
@Slf4j
@RestController
public class GatewayFallbackController {

    @RequestMapping("/fallback/accounts")
    public Mono<ResponseEntity<ErrorResponse>> accountFallback() {
        log.warn("Circuit breaker open for account-service — returning fallback response");
        ErrorResponse error = ErrorResponse.of(
                ErrorCode.SERVICE_UNAVAILABLE,
                "Account service is temporarily unavailable. Please try again later.",
                "/fallback/accounts");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));
    }

    @RequestMapping("/fallback/transactions")
    public Mono<ResponseEntity<ErrorResponse>> transactionFallback() {
        log.warn("Circuit breaker open for transaction-service — returning fallback response");
        ErrorResponse error = ErrorResponse.of(
                ErrorCode.SERVICE_UNAVAILABLE,
                "Transaction service is temporarily unavailable. Please try again later.",
                "/fallback/transactions");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));
    }

    @RequestMapping("/fallback/payments")
    public Mono<ResponseEntity<ErrorResponse>> paymentFallback() {
        log.warn("Circuit breaker open for payment-service — returning fallback response");
        ErrorResponse error = ErrorResponse.of(
                ErrorCode.SERVICE_UNAVAILABLE,
                "Payment service is temporarily unavailable. Please try again later.",
                "/fallback/payments");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));
    }

    @RequestMapping("/fallback/reports")
    public Mono<ResponseEntity<ErrorResponse>> reportFallback() {
        log.warn("Circuit breaker open for report-service — returning fallback response");
        ErrorResponse error = ErrorResponse.of(
                ErrorCode.SERVICE_UNAVAILABLE,
                "Report service is temporarily unavailable. Please try again later.",
                "/fallback/reports");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));
    }

    @RequestMapping("/fallback")
    public Mono<ResponseEntity<ErrorResponse>> genericFallback() {
        log.warn("Circuit breaker open for unknown-service — returning fallback response");
        ErrorResponse error = ErrorResponse.of(
                ErrorCode.SERVICE_UNAVAILABLE,
                "The requested service is temporarily unavailable. Please try again later.",
                "/fallback");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));
    }
}
