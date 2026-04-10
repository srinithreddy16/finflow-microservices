package com.finflow.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Transaction Service — core financial domain service.
 * Implements CQRS + Event Sourcing pattern.
 * Write side: commands -> aggregate -> events appended to EventStore.
 * Read side: projections rebuilt from events (Day 5).
 * Also acts as Choreography Saga initiator for payment flow.
 * Calls fraud-detection-service via gRPC before committing a transaction.
 * Port: 8083.
 */
@SpringBootApplication
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
