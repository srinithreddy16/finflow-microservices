CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL UNIQUE,
    sender_account_id VARCHAR(36) NOT NULL,
    receiver_account_id VARCHAR(36),
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,
    correlation_id VARCHAR(100),
    saga_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_payments_transaction_id
    ON payments(transaction_id);
CREATE INDEX idx_payments_sender_account
    ON payments(sender_account_id);
CREATE INDEX idx_payments_status
    ON payments(status);
CREATE INDEX idx_payments_created_at
    ON payments(created_at DESC);
CREATE INDEX idx_payments_sender_status
    ON payments(sender_account_id, status);

COMMENT ON TABLE payments IS
    'One row per PaymentInitiated event received from transaction-service.
     Idempotent: duplicate transactionIds are rejected via unique constraint.';
