package com.finflow.transaction.aggregate;

import com.finflow.transaction.event.TransactionCompletedEvent;
import com.finflow.transaction.event.TransactionCreatedEvent;
import com.finflow.transaction.event.TransactionEvent;
import com.finflow.transaction.event.TransactionFailedEvent;
import com.finflow.transaction.event.TransactionReversedEvent;
import com.finflow.transaction.model.TransactionStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;


//Event Sourcing = Store every change as an event instead of storing only the latest state
@Slf4j
public class TransactionAggregate {

    private String transactionId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private String correlationId;
    private long currentSequenceNumber = 0;
    private final List<TransactionEvent> uncommittedEvents = new ArrayList<>();

    public TransactionAggregate() {}  //When we rebuild an aggregate from the EventStore, we create an empty aggregate first and then replay all its historical events onto it. Without this empty constructor that process cannot start.

    public static TransactionAggregate create(
            String transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            String description,
            String initiatedBy,
            String correlationId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency must not be blank");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("AccountId must not be blank");
        }

        TransactionAggregate aggregate = new TransactionAggregate();
        aggregate.raiseEvent(
                new TransactionCreatedEvent(
                        transactionId,
                        accountId,
                        amount,
                        currency,
                        description,
                        initiatedBy,
                        correlationId,
                        1L));
        return aggregate;
    }

    public void complete(String paymentId, String correlationId) {
        if (status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Cannot complete transaction in status: " + status);
        }
        raiseEvent(
                new TransactionCompletedEvent(
                        transactionId, paymentId, correlationId, currentSequenceNumber + 1));
    }

    public void fail(String reason, String failureCode, String correlationId) {
        if (status != TransactionStatus.PENDING) {
            throw new IllegalStateException("Cannot fail transaction in status: " + status);
        }
        raiseEvent(
                new TransactionFailedEvent(
                        transactionId,
                        reason,
                        failureCode,
                        correlationId,
                        currentSequenceNumber + 1));
    }

    public void reverse(String reversalReason, String reversedBy, String correlationId) {
        if (status != TransactionStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Only COMPLETED transactions can be reversed. Current status: " + status);
        }
        raiseEvent(
                new TransactionReversedEvent(
                        transactionId,
                        reversalReason,
                        reversedBy,
                        correlationId,
                        currentSequenceNumber + 1));
    }

    private void raiseEvent(TransactionEvent event) {                                             /*This is the most important internal method. Every time a business operation happens it calls raiseEvent() which does two things:
                                                                                                  First — adds the event to uncommittedEvents. These will be saved to the EventStore by TransactionCommandHandler after this method returns.
                                                                                                   Second — immediately calls applyEvent() to update the in-memory state right now. This is why after calling aggregate.complete(...)
                                                                                               the aggregate's status field immediately becomes COMPLETED — even before anything is saved to the database.*/
        uncommittedEvents.add(event);
        applyEvent(event);
        log.debug("Event raised: {} for transaction: {}", event.getEventType(), transactionId);
    }

    public void apply(TransactionCreatedEvent event) {
        transactionId = event.getAggregateId();
        accountId = event.getAccountId();
        amount = event.getAmount();
        currency = event.getCurrency();
        status = TransactionStatus.PENDING;
        correlationId = event.getCorrelationId();
        currentSequenceNumber = event.getSequenceNumber();
    }

    public void apply(TransactionCompletedEvent event) {
        status = TransactionStatus.COMPLETED;
        currentSequenceNumber = event.getSequenceNumber();
    }

    public void apply(TransactionFailedEvent event) {
        status = TransactionStatus.FAILED;
        currentSequenceNumber = event.getSequenceNumber();
    }

    public void apply(TransactionReversedEvent event) {
        status = TransactionStatus.REVERSED;
        currentSequenceNumber = event.getSequenceNumber();
    }

    public static TransactionAggregate reconstitute(List<TransactionEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Events list cannot be empty");
        }
        TransactionAggregate aggregate = new TransactionAggregate();
        for (TransactionEvent event : events) {
            aggregate.applyEvent(event);
        }
        return aggregate;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public long getCurrentSequenceNumber() {
        return currentSequenceNumber;
    }

    public List<TransactionEvent> getUncommittedEvents() {
        return List.copyOf(uncommittedEvents);
    }

    public void markEventsAsCommitted() {
        uncommittedEvents.clear();
    }

    private void applyEvent(TransactionEvent event) {
        if (event instanceof TransactionCreatedEvent createdEvent) {
            apply(createdEvent);
            return;
        }
        if (event instanceof TransactionCompletedEvent completedEvent) {
            apply(completedEvent);
            return;
        }
        if (event instanceof TransactionFailedEvent failedEvent) {
            apply(failedEvent);
            return;
        }
        if (event instanceof TransactionReversedEvent reversedEvent) {
            apply(reversedEvent);
            return;
        }
        throw new IllegalArgumentException("Unsupported event type: " + event.getClass().getName());
    }
}
