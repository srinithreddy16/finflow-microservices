package com.finflow.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Payment Service — handles ledger management and settlement.
 *
 * <p>Choreography Saga participant in the payment flow:
 *
 * <ol>
 *   <li>Consumes PaymentInitiated from RabbitMQ payment.events exchange
 *   <li>Processes double-entry ledger entries (debit sender, credit receiver)
 *   <li>Publishes PaymentCompleted or PaymentFailed to RabbitMQ
 *   <li>Publishes to Kafka: analytics, audit-log, notify-events topics
 * </ol>
 *
 * <p>Double-entry bookkeeping:
 *
 * <p>Every payment creates TWO ledger entries:
 *
 * <ul>
 *   <li>DEBIT on the sender account (money going out)
 *   <li>CREDIT on the receiver account (money coming in)
 * </ul>
 *
 * <p>This ensures the books always balance.
 *
 * <p>Port: 8084
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
