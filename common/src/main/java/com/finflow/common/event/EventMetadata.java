package com.finflow.common.event;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Cross-cutting metadata embedded in every domain event for tracing and correlation.
 * <p>
 * Carries identifiers used by observability (trace), workflow correlation, and provenance
 * (source system or bounded context) alongside the wall-clock time the metadata was created.
 */
@Getter
@Builder
public class EventMetadata {

    @Builder.Default
    private final String eventId = UUID.randomUUID().toString();

    private final String correlationId;
    private final String traceId;
    private final String source;

    @Builder.Default
    private final Instant occurredOn = Instant.now();

    /**
     * Builds metadata with generated {@code eventId} and {@code occurredOn}, wiring correlation,
     * trace, and source only.
     */
    public static EventMetadata of(String correlationId, String traceId, String source) {
        return EventMetadata.builder()
                .correlationId(correlationId)
                .traceId(traceId)
                .source(source)
                .build();
    }
}
