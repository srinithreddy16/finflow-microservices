package com.finflow.transaction.event;

import lombok.Getter;

@Getter
public class TransactionFailedEvent extends TransactionEvent {

    private final String reason;
    private final String failureCode;

    public TransactionFailedEvent(
            String transactionId,
            String reason,
            String failureCode,
            String correlationId,
            long sequenceNumber) {
        super(transactionId, "TRANSACTION_FAILED", correlationId, sequenceNumber);
        this.reason = reason;
        this.failureCode = failureCode;
    }
}
