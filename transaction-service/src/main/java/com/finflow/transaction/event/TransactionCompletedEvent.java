package com.finflow.transaction.event;

import java.time.Instant;
import lombok.Getter;

@Getter
public class TransactionCompletedEvent extends TransactionEvent {

    private final String paymentId;
    private final Instant completedAt;

    public TransactionCompletedEvent(
            String transactionId, String paymentId, String correlationId, long sequenceNumber) {
        super(transactionId, "TRANSACTION_COMPLETED", correlationId, sequenceNumber);
        this.paymentId = paymentId;
        this.completedAt = Instant.now();
    }
}
