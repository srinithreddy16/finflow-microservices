-- Seed initial balance for test account
-- In production, initial balances are set by account onboarding process

INSERT INTO payments (id, transaction_id, sender_account_id,
                      amount, currency, status, correlation_id)
VALUES (
    'payment-seed-001',
    'tx-seed-001',
    'account-001',
    10000.00,
    'USD',
    'COMPLETED',
    'seed-correlation-001'
);

-- Create initial CREDIT for test account (seed balance of $10,000)
INSERT INTO ledger_entries (id, payment_id, account_id, entry_type,
                             amount, currency, description, running_balance)
VALUES (
    'ledger-seed-001',
    'payment-seed-001',
    'account-001',
    'CREDIT',
    10000.00,
    'USD',
    'Initial account funding',
    10000.00
);
