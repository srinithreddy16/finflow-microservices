ALTER TABLE transaction_projection
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(100);
