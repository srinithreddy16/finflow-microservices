package com.finflow.transaction.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// This is the JPA entity — it represents one row in the event_store table. Every single event that ever happens gets stored as one row here.

@Entity
@Table(
        name = "event_store",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"aggregate_id", "sequence_number"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventStoreEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_data", nullable = false, columnDefinition = "TEXT")
    private String eventData;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "occurred_on", nullable = false)
    private Instant occurredOn;

    @Builder.Default
    @Column(name = "event_version", nullable = false)
    private int eventVersion = 1;
}
