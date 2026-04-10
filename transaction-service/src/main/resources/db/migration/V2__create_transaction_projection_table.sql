-- This table is the READ MODEL (CQRS query side) built in Day 5.
-- Created here so the service starts without errors.
-- It will be populated by TransactionProjectionUpdater in Day 5.

CREATE TABLE transaction_projection (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description TEXT,
    initiated_by VARCHAR(100),
    payment_id VARCHAR(36),
    failure_reason TEXT,
    reversal_reason TEXT,
    reversed_by VARCHAR(100),
    correlation_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    reversed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tx_projection_account_id 
    ON transaction_projection(account_id);
CREATE INDEX idx_tx_projection_status 
    ON transaction_projection(status);
CREATE INDEX idx_tx_projection_created_at 
    ON transaction_projection(created_at);

COMMENT ON TABLE transaction_projection IS 
    'CQRS read model. Rebuilt by replaying events from event_store.
     DO NOT write to this table directly — use TransactionProjectionUpdater.';
