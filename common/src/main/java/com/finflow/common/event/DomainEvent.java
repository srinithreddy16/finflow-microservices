package com.finflow.common.event;

import java.time.Instant;

/**
 * Contract for every domain event published or persisted in FinFlow.
 * <p>
 * All domain events across services must implement this interface so that event envelopes,
 * serialization, auditing, and tracing can rely on a consistent shape (identity, causality,
 * and temporal metadata).
 */
public interface DomainEvent {

    String getEventId();

    String getAggregateId();

    String getEventType();

    Instant getOccurredOn();

    String getCorrelationId();
}
