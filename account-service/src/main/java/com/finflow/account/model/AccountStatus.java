package com.finflow.account.model;

/**
 * Lifecycle state of an account in FinFlow.
 */
public enum AccountStatus {
    PENDING("Account created, awaiting KYC verification"),
    ACTIVE("Account verified and active"),
    SUSPENDED("Account suspended due to policy violation"),
    CLOSED("Account permanently closed");

    private final String description;

    AccountStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
