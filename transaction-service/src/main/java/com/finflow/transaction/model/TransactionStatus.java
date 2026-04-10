package com.finflow.transaction.model;

/**
 * Represents the transaction lifecycle in the system.
 *
 * <p>State transitions:
 * PENDING -> COMPLETED (happy path)
 * PENDING -> FAILED (fraud detected or payment failed)
 * COMPLETED -> REVERSED (reversal requested)
 */
public enum TransactionStatus {
    PENDING("Transaction created, awaiting fraud check and payment processing"),
    COMPLETED("Transaction successfully processed and settled"),
    FAILED("Transaction failed due to fraud detection or processing error"),
    REVERSED("Transaction reversed by customer or compliance request");

    private final String description;

    TransactionStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
