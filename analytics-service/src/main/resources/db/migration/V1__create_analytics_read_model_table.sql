CREATE TABLE analytics_read_model (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    total_transactions BIGINT NOT NULL DEFAULT 0,
    completed_transactions BIGINT NOT NULL DEFAULT 0,
    failed_transactions BIGINT NOT NULL DEFAULT 0,
    reversed_transactions BIGINT NOT NULL DEFAULT 0,
    total_volume DECIMAL(19, 4) NOT NULL DEFAULT 0,
    completed_volume DECIMAL(19, 4) NOT NULL DEFAULT 0,
    fraud_flags BIGINT NOT NULL DEFAULT 0,
    avg_transaction_amount DECIMAL(19, 4) DEFAULT 0,
    last_updated TIMESTAMP,
    CONSTRAINT uq_account_date_currency
        UNIQUE (account_id, date, currency)
);

CREATE INDEX idx_analytics_account_id
    ON analytics_read_model(account_id);
CREATE INDEX idx_analytics_date
    ON analytics_read_model(date DESC);
CREATE INDEX idx_analytics_account_date
    ON analytics_read_model(account_id, date DESC);
CREATE INDEX idx_analytics_total_volume
    ON analytics_read_model(total_volume DESC);

COMMENT ON TABLE analytics_read_model IS
    'CQRS read model for analytics. Built by consuming Kafka events.
     One row per account per day per currency.
     NEVER written to directly from the command side.';
