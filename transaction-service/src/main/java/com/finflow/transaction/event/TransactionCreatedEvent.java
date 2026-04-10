package com.finflow.transaction.event;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class TransactionCreatedEvent extends TransactionEvent {

    private final String accountId;
    private final BigDecimal amount;
    private final String currency;
    private final String description;
    private final String initiatedBy;

    public TransactionCreatedEvent(
            String transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            String description,
            String initiatedBy,
            String correlationId,
            long sequenceNumber) {
        super(transactionId, "TRANSACTION_CREATED", correlationId, sequenceNumber);
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.initiatedBy = initiatedBy;
    }

    @Override
    public String getEventType() {
        return "TRANSACTION_CREATED";
    }
}
