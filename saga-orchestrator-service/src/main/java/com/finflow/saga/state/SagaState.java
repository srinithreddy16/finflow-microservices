package com.finflow.saga.state;

/**
 * State machine for the Account Onboarding Orchestration Saga. The orchestrator transitions
 * through these states as it sends commands and receives replies from saga participants.
 *
 * <p>Happy path: STARTED -> STEP_1_PENDING -> STEP_1_COMPLETED -> STEP_2_PENDING -> STEP_2_COMPLETED
 * -> STEP_3_PENDING -> STEP_3_COMPLETED -> STEP_4_PENDING -> COMPLETED
 *
 * <p>Failure path (e.g. KYC fails at step 2): STEP_2_PENDING -> COMPENSATING -> COMPENSATION_STEP_3
 * -> COMPENSATION_STEP_2 -> COMPENSATION_STEP_1 -> FAILED
 */
public enum SagaState {
    STARTED("Saga has been initiated, first step pending"),
    STEP_1_PENDING("CREATE_ACCOUNT command sent, awaiting reply"),
    STEP_1_COMPLETED("Account created successfully"),
    STEP_2_PENDING("VERIFY_KYC command sent, awaiting reply"),
    STEP_2_COMPLETED("KYC verification passed"),
    STEP_3_PENDING("CREATE_KEYCLOAK_USER command sent, awaiting reply"),
    STEP_3_COMPLETED("Keycloak user created successfully"),
    STEP_4_PENDING("SEND_WELCOME_EMAIL command sent, awaiting reply"),
    COMPLETED("All saga steps completed successfully"),
    COMPENSATING("Saga failed, running compensation in reverse"),
    COMPENSATION_STEP_3("Compensating step 3: deleting Keycloak user"),
    COMPENSATION_STEP_2("Compensating step 2: reversing KYC status"),
    COMPENSATION_STEP_1("Compensating step 1: deleting account"),
    FAILED("Saga failed and compensation completed"),
    COMPENSATION_FAILED("Saga failed and compensation also failed -- manual intervention required");

    private final String description;

    SagaState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == COMPENSATION_FAILED;
    }

    public boolean isCompensating() {
        return this == COMPENSATING
                || this == COMPENSATION_STEP_3
                || this == COMPENSATION_STEP_2
                || this == COMPENSATION_STEP_1;
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public SagaState nextCompensationState() {
        return switch (this) {
            case COMPENSATING -> COMPENSATION_STEP_3;
            case COMPENSATION_STEP_3 -> COMPENSATION_STEP_2;
            case COMPENSATION_STEP_2 -> COMPENSATION_STEP_1;
            case COMPENSATION_STEP_1 -> FAILED;
            default -> FAILED;
        };
    }
}
