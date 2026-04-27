package com.finflow.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Saga Orchestrator Service -- central coordinator for the Account Onboarding Orchestration
 * Saga.
 *
 * <p>Pattern: Orchestration Saga (NOT Choreography) The orchestrator sends explicit commands to
 * each participant and waits for a reply before proceeding to the next step. On any failure, it
 * triggers compensation in reverse order.
 *
 * <p>Onboarding Saga Steps (in order): Step 1: CREATE_ACCOUNT -- account-service Step 2:
 * VERIFY_KYC -- account-service (calls fraud-service internally) Step 3: CREATE_KEYCLOAK_USER --
 * account-service (Keycloak integration) Step 4: SEND_WELCOME_EMAIL -- notification-service
 *
 * <p>On failure at any step: - Compensation runs in reverse: Step 4->3->2->1 - Each compensation
 * undoes what the step did
 *
 * <p>Communication: RabbitMQ only - Sends commands to: saga.commands exchange - Receives replies
 * from: saga.replies queue
 *
 * <p>State persistence: saga_db (PostgreSQL) - Every saga instance state is persisted so it
 * survives restarts
 *
 * <p>Port: 8085
 */
@SpringBootApplication
public class SagaOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagaOrchestratorApplication.class, args);
    }
}
