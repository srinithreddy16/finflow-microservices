CREATE TABLE ledger_entries (
    id VARCHAR(36) PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description TEXT,
    running_balance DECIMAL(19, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_payment_id
    ON ledger_entries(payment_id);
CREATE INDEX idx_ledger_account_id
    ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entry_type
    ON ledger_entries(entry_type);
CREATE INDEX idx_ledger_account_created
    ON ledger_entries(account_id, created_at DESC);

COMMENT ON TABLE ledger_entries IS
    'Immutable double-entry bookkeeping records.
     Every payment creates exactly 2 rows: one DEBIT and one CREDIT.
     NEVER UPDATE or DELETE rows in this table.
     Running balance is calculated at insert time for performance.';
