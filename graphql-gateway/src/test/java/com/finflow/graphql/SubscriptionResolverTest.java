package com.finflow.graphql;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
        })
class SubscriptionResolverTest {

    @LocalServerPort private int port;
    @MockBean private JwtDecoder jwtDecoder;

    @Test
    void fraudAlerts_StreamsAlerts_AfterSubscription() {
        WebSocketGraphQlTester webSocketTester =
                WebSocketGraphQlTester.builder("ws://localhost:" + port + "/graphql", new ReactorNettyWebSocketClient())
                        .build();

        webSocketTester
                .document("subscription { fraudAlerts(accountId: \"acc-001\") { transactionId fraudScore } }")
                .executeSubscription()
                .toFlux("fraudAlerts", FraudAlertType.class)
                .take(2)
                .as(StepVerifier::create)
                .expectNextCount(2)
                .thenCancel()
                .verify(Duration.ofSeconds(15));
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
}
