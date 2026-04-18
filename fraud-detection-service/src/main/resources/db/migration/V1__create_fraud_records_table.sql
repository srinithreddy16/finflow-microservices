CREATE TABLE fraud_records (
    id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL UNIQUE,
    account_id VARCHAR(36) NOT NULL,
    fraud_score INT NOT NULL DEFAULT 0,
    flagged BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    failure_code VARCHAR(50),
    triggered_rules TEXT,
    amount DECIMAL(19, 4),
    currency VARCHAR(3),
    country_code VARCHAR(3),
    check_type VARCHAR(20) NOT NULL DEFAULT 'SYNC_GRPC',
    evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fraud_records_transaction_id
    ON fraud_records(transaction_id);
CREATE INDEX idx_fraud_records_account_id
    ON fraud_records(account_id);
CREATE INDEX idx_fraud_records_flagged
    ON fraud_records(flagged);
CREATE INDEX idx_fraud_records_evaluated_at
    ON fraud_records(evaluated_at);
CREATE INDEX idx_fraud_records_account_evaluated
    ON fraud_records(account_id, evaluated_at DESC);

COMMENT ON TABLE fraud_records IS
    'Audit log of every fraud check performed.
     Every transaction checked by gRPC or Kafka has exactly one record here.
     Used by VelocityRule and GeolocationRule for pattern detection.';
COMMENT ON COLUMN fraud_records.triggered_rules IS
    'JSON array of rule names that fired e.g. ["AMOUNT_THRESHOLD","VELOCITY"]';
COMMENT ON COLUMN fraud_records.check_type IS
    'SYNC_GRPC: called synchronously by transaction-service
     ASYNC_KAFKA: processed from fraud-events topic';
