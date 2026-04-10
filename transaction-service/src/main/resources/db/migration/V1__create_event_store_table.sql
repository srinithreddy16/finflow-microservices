CREATE TABLE event_store (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data TEXT NOT NULL,
    sequence_number BIGINT NOT NULL,
    correlation_id VARCHAR(100),
    occurred_on TIMESTAMP NOT NULL,
    event_version INT NOT NULL DEFAULT 1,
    CONSTRAINT uq_event_store_aggregate_seq 
        UNIQUE (aggregate_id, sequence_number)
);

CREATE INDEX idx_event_store_aggregate_id 
    ON event_store(aggregate_id);
CREATE INDEX idx_event_store_event_type 
    ON event_store(event_type);
CREATE INDEX idx_event_store_occurred_on 
    ON event_store(occurred_on);

COMMENT ON TABLE event_store IS 
    'Append-only event log. Never UPDATE or DELETE rows in this table.';
COMMENT ON COLUMN event_store.event_data IS 
    'JSON serialized event payload';
COMMENT ON COLUMN event_store.sequence_number IS 
    'Monotonically increasing per aggregate_id. Used for optimistic concurrency.';
