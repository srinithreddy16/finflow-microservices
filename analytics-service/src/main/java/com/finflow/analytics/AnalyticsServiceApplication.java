package com.finflow.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Analytics Service -- real-time transaction analytics and aggregation.
 *
 * <p>Kafka Consumer Groups:
 *
 * <p>- 'analytics-service' on topics: transactions, fraud-events, analytics
 *
 * <p>Responsibilities:
 *
 * <p>1. Consume transaction and fraud events from Kafka 2. Aggregate into AnalyticsReadModel
 * (PostgreSQL read model) 3. Maintain real-time Redis counters for instant stats 4. Expose REST
 * endpoints called by the GraphQL gateway
 *
 * <p>This service is a pure READ service for analytics purposes. It does NOT write to any other
 * service's database. It does NOT produce Kafka events. It does NOT use RabbitMQ.
 *
 * <p>Port: 8088
 */
@SpringBootApplication
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
