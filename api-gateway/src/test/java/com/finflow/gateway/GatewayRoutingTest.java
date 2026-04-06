package com.finflow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9090/realms/finflow",
        "management.health.redis.enabled=false"
})
class GatewayRoutingTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void testHealthEndpointIsPublic() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testProtectedRouteReturns401WithoutToken() {
        webTestClient.get()
                .uri("/api/transactions/123")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testProtectedRouteReturns401WithInvalidToken() {
        JwtValidationException jwtValidationException =
                new JwtValidationException("Invalid JWT", java.util.List.of(new OAuth2Error("invalid_token")));
        when(reactiveJwtDecoder.decode(anyString()))
                .thenReturn(Mono.error(jwtValidationException));

        webTestClient.get()
                .uri("/api/transactions/123")
                .header("Authorization", "Bearer invalid-token-here")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testCorrelationIdIsAddedToResponse() {
        webTestClient.get()
                .uri("/auth/ping")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().valueMatches("X-Correlation-Id", "\\S+");
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
            return http
                    .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .build();
        }
    }
}
