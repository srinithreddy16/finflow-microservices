package com.finflow.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fraud Detection Service — dual-protocol service exposing:
 *
 * <ol>
 *   <li>gRPC server on port 9001: synchronous fraud check called by transaction-service before every
 *       payment is committed. Wrapped by Resilience4j circuit breaker in the client.
 *   <li>HTTP on port 8086: actuator endpoints + Kafka consumer for async fraud event monitoring.
 * </ol>
 *
 * <p>Fraud scoring uses a rule-based engine that evaluates:
 *
 * <ul>
 *   <li>Amount threshold (transactions &gt; $10,000)
 *   <li>Velocity (&gt; 5 transactions per account in 60 seconds)
 *   <li>Geolocation (country mismatch on account vs transaction)
 * </ul>
 *
 * <p>All fraud checks are persisted to fraud_db for audit purposes.
 */
@SpringBootApplication
public class FraudDetectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudDetectionApplication.class, args);
    }
}
