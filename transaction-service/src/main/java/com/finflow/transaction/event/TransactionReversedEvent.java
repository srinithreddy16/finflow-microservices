package com.finflow.transaction.event;

import java.time.Instant;
import lombok.Getter;

@Getter
public class TransactionReversedEvent extends TransactionEvent {

    private final String reversalReason;
    private final String reversedBy;
    private final Instant reversedAt;

    public TransactionReversedEvent(
            String transactionId,
            String reversalReason,
            String reversedBy,
            String correlationId,
            long sequenceNumber) {
        super(transactionId, "TRANSACTION_REVERSED", correlationId, sequenceNumber);
        this.reversalReason = reversalReason;
        this.reversedBy = reversedBy;
        this.reversedAt = Instant.now();
    }
}
