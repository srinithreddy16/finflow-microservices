package com.finflow.transaction.event;

import java.util.List;

/**
 * EventStore — append-only log of all transaction domain events.
 * Events are NEVER updated or deleted.
 * The current state of any transaction is derived by replaying its events.
 * This is the foundation of Event Sourcing in FinFlow.
 * This is just a Java interface — it defines the rules. It says "whoever implements me must be able to do these 4 things":
 */
public interface EventStore {

    void append(TransactionEvent event);  // // → Save a new event to the store. Called every time something happens

    List<TransactionEvent> findByAggregateId(String aggregateId);   // → Give me ALL events that ever happened to transaction "tx-001".

    List<TransactionEvent> findByAggregateIdAndSequenceNumberGreaterThan(
            String aggregateId, long sequenceNumber);  // Give me only RECENT events after a certain point.

    long getNextSequenceNumber(String aggregateId);   // What sequence number should the NEXT event for this transaction get?
}
