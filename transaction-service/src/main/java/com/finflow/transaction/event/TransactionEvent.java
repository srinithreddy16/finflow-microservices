package com.finflow.transaction.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.finflow.common.event.DomainEvent;
import com.finflow.common.util.IdGenerator;
import java.time.Instant;
import lombok.Getter;

/**
 * Base class for all Transaction domain events.
 * Every state change in a transaction is recorded as an immutable event.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TransactionCreatedEvent.class, name = "TRANSACTION_CREATED"),
    @JsonSubTypes.Type(value = TransactionCompletedEvent.class, name = "TRANSACTION_COMPLETED"),
    @JsonSubTypes.Type(value = TransactionFailedEvent.class, name = "TRANSACTION_FAILED"),
    @JsonSubTypes.Type(value = TransactionReversedEvent.class, name = "TRANSACTION_REVERSED")
})
@Getter
public abstract class TransactionEvent implements DomainEvent {

    private final String eventId;
    private final String aggregateId;
    private final String eventType;
    private final Instant occurredOn;
    private final String correlationId;
    private final long sequenceNumber;

    protected TransactionEvent(
            String transactionId, String eventType, String correlationId, long sequenceNumber) {
        this.eventId = IdGenerator.generate();
        this.aggregateId = transactionId;
        this.eventType = eventType;
        this.occurredOn = Instant.now();
        this.correlationId = correlationId;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public String getAggregateId() {
        return aggregateId;
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public Instant getOccurredOn() {
        return occurredOn;
    }

    @Override
    public String getCorrelationId() {
        return correlationId;
    }
}
