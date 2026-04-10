package com.finflow.transaction.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
/*
This is the implementation of the EventStore interface. This is where the real work happens —
it bridges the gap between the Java event objects and the database rows.
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class EventStoreImpl implements EventStore {

    private final EventStoreRepository eventStoreRepository;
    private final ObjectMapper objectMapper;


    // append() : Java event object → database row
    @Override
    public void append(TransactionEvent event) {
        String eventJson = toJson(event);
        EventStoreEntry entry =
                EventStoreEntry.builder()
                        .aggregateId(event.getAggregateId())
                        .eventType(event.getEventType())
                        .eventData(eventJson)
                        .sequenceNumber(event.getSequenceNumber())
                        .correlationId(event.getCorrelationId())
                        .occurredOn(event.getOccurredOn())
                        .build();

        eventStoreRepository.save(entry);
        log.debug(
                "Event appended: type={}, aggregateId={}, seq={}",
                event.getEventType(),
                event.getAggregateId(),
                event.getSequenceNumber());
    }

    @Override
    public List<TransactionEvent> findByAggregateId(String aggregateId) {
        List<TransactionEvent> events =
                eventStoreRepository.findByAggregateIdOrderBySequenceNumberAsc(aggregateId).stream()
                        .map(this::toEvent)
                        .toList();
        log.debug("Loaded {} events for aggregateId: {}", events.size(), aggregateId);
        return events;
    }

    @Override
    public List<TransactionEvent> findByAggregateIdAndSequenceNumberGreaterThan(
            String aggregateId, long sequenceNumber) {
        return eventStoreRepository.findByAggregateIdOrderBySequenceNumberAsc(aggregateId).stream()
                .filter(entry -> entry.getSequenceNumber() > sequenceNumber)
                .map(this::toEvent)
                .toList();
    }

    @Override
    public long getNextSequenceNumber(String aggregateId) {
        return eventStoreRepository.countByAggregateId(aggregateId) + 1;
    }

    private String toJson(TransactionEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize transaction event: " + event.getEventType(), e);
        }
    }

    private TransactionEvent toEvent(EventStoreEntry entry) {
        try {
            return switch (entry.getEventType()) {
                case "TRANSACTION_CREATED" ->
                        objectMapper.readValue(entry.getEventData(), TransactionCreatedEvent.class);
                case "TRANSACTION_COMPLETED" ->
                        objectMapper.readValue(
                                entry.getEventData(), TransactionCompletedEvent.class);
                case "TRANSACTION_FAILED" ->
                        objectMapper.readValue(entry.getEventData(), TransactionFailedEvent.class);
                case "TRANSACTION_REVERSED" ->
                        objectMapper.readValue(
                                entry.getEventData(), TransactionReversedEvent.class);
                default ->
                        throw new IllegalArgumentException(
                                "Unsupported transaction event type: " + entry.getEventType());
            };
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to deserialize transaction event: " + entry.getEventType(), e);
        }
    }
}
