package com.finflow.fraud.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j wiring for the fraud detection service.
 *
 * <p>Circuit breaker registry for fraud service outbound calls. Although the fraud service itself
 * is a server (not a client), it may call external KYC services in future iterations. Resilience4j
 * is pre-configured for those future outbound calls (see {@code application.yml} for instance
 * tuning).
 */
@Configuration
public class Resilience4jConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }
}
