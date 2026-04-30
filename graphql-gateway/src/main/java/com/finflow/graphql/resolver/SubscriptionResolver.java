package com.finflow.graphql.resolver;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

/**
 * GraphQL Subscription resolvers for real-time updates.
 *
 * <p>Transport: WebSocket (ws://host/graphql) Protocol: graphql-transport-ws (modern,
 * recommended)
 *
 * <p>Production implementation: Instead of Flux.interval() simulation, connect these to: - Redis
 * pub/sub channel for fraud alerts (fraud-detection-service publishes to Redis channel when a
 * transaction is flagged) - Kafka reactive consumer for transaction updates
 *
 * <p>The Flux.interval() simulation is intentional for Day 10 to demonstrate the subscription
 * mechanism without requiring additional infrastructure. Replace with real data source in Day 12.
 *
 * <p>Client usage example (GraphQL): subscription { fraudAlerts(accountId: 'acc-001') {
 * transactionId fraudScore reason occurredAt } }
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class SubscriptionResolver {

    @SubscriptionMapping
    public Publisher<FraudAlertType> fraudAlerts(
            @Argument String accountId, @AuthenticationPrincipal Jwt jwt) {
        log.info("Fraud alerts subscription started for accountId: {}", accountId);

        if (accountId == null || accountId.isBlank()) {
            List<String> roles = extractRoles(jwt);
            boolean isAdmin =
                    roles.stream()
                            .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                            .anyMatch("BUSINESS_ADMIN"::equals);
            if (!isAdmin) {
                throw FinFlowException.forbidden("Access denied");
            }
        }

        return Flux.interval(Duration.ofSeconds(5))
                .map(
                        tick ->
                                new FraudAlertType(
                                        "simulated-tx-" + tick,
                                        accountId != null ? accountId : "system",
                                        50 + ThreadLocalRandom.current().nextInt(50),
                                        "Simulated fraud alert for testing",
                                        "SIMULATION",
                                        Instant.now()))
                .filter(alert -> accountId == null || alert.accountId().equals(accountId))
                .doOnCancel(() -> log.info("Fraud alerts subscription cancelled"))
                .doOnError(
                        ex ->
                                log.error(
                                        "Fraud alerts subscription error: {}", ex.getMessage(), ex));
    }

    @SubscriptionMapping
    public Publisher<TransactionType> transactionUpdates(
            @Argument String accountId, @AuthenticationPrincipal Jwt jwt) {
        if (accountId == null || accountId.isBlank()) {
            throw FinFlowException.badRequest(
                    ErrorCode.VALIDATION_FAILED, "accountId must not be blank");
        }

        return Flux.interval(Duration.ofSeconds(10))
                .map(
                        tick ->
                                new TransactionType(
                                        "tx-" + tick,
                                        accountId,
                                        BigDecimal.valueOf(100),
                                        "USD",
                                        "PENDING",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        Instant.now(),
                                        null,
                                        Instant.now()))
                .take(100);
    }

    public record FraudAlertType(
            String transactionId,
            String accountId,
            int fraudScore,
            String reason,
            String failureCode,
            Instant occurredAt) {}

    public record TransactionType(
            String id,
            String accountId,
            BigDecimal amount,
            String currency,
            String status,
            String description,
            String initiatedBy,
            String paymentId,
            String failureReason,
            String correlationId,
            Instant createdAt,
            Instant completedAt,
            Instant updatedAt) {}

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        if (jwt == null) {
            return Collections.emptyList();
        }

        List<String> direct = jwt.getClaimAsStringList("realm_access.roles");
        if (direct != null) {
            return direct;
        }

        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        return Collections.emptyList();
    }
}
