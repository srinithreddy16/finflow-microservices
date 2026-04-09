package com.finflow.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Account Service — manages users, tenants, and KYC status.
 * Participates in the Orchestration Saga for account onboarding.
 * Publishes audit-log events to Kafka.
 * Communicates via RabbitMQ for saga commands and replies.
 * Port: 8082.
 */
@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
