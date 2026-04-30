package com.finflow.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GraphQL Gateway -- flexible read API for admin dashboards and finance analyst users.
 *
 * <p>This service sits ALONGSIDE the REST API Gateway:
 *
 * <p>- REST Gateway (:8080) -> handles ALL write operations (create transactions, initiate
 * payments, etc.) - GraphQL Gateway (:8081) -> handles ALL flexible read queries (analytics,
 * reports, account profiles, fraud data)
 *
 * <p>What GraphQL solves here: Instead of 3-4 separate REST calls with frontend join logic, a
 * single GraphQL query fans out to multiple services via REST clients and returns a unified
 * response.
 *
 * <p>Example: A finance analyst dashboard needs transaction summaries, fraud rates, and recent
 * reports. One GraphQL query returns all three in a single response, with DataLoader batching DB
 * calls to prevent N+1.
 *
 * <p>Services called via REST:
 *
 * <p>- analytics-service :8088 -> aggregated analytics data - report-service :8089 -> PDF/CSV
 * reports and download URLs - account-service :8082 -> account and tenant profiles
 *
 * <p>Real-time features:
 *
 * <p>- GraphQL Subscriptions over WebSocket for live fraud alerts
 *
 * <p>Port: 8081 GraphQL endpoint: POST /graphql WebSocket endpoint: ws://host/graphql (for
 * subscriptions) GraphiQL UI: /graphiql (dev only)
 */
@SpringBootApplication
public class GraphqlGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphqlGatewayApplication.class, args);
    }
}
