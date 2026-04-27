CREATE TABLE saga_instances (
    saga_id VARCHAR(36) PRIMARY KEY,
    saga_type VARCHAR(50) NOT NULL,
    state VARCHAR(50) NOT NULL,
    current_step INT NOT NULL DEFAULT 0,
    account_id VARCHAR(36),
    keycloak_user_id VARCHAR(36),
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone_number VARCHAR(20),
    tenant_id VARCHAR(36),
    correlation_id VARCHAR(100),
    failure_reason TEXT,
    failure_step INT,
    compensation_reason TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_saga_state
    ON saga_instances(state);
CREATE INDEX idx_saga_email
    ON saga_instances(email);
CREATE INDEX idx_saga_correlation
    ON saga_instances(correlation_id);
CREATE INDEX idx_saga_account_id
    ON saga_instances(account_id);
CREATE INDEX idx_saga_created_at
    ON saga_instances(created_at DESC);
CREATE INDEX idx_saga_type_state
    ON saga_instances(saga_type, state);

COMMENT ON TABLE saga_instances IS
    'Persistent state for Orchestration Saga instances.
     One row per saga execution. Never deleted — retained for audit.
     Terminal states: COMPLETED, FAILED, COMPENSATION_FAILED.
     Non-terminal rows represent in-progress sagas.';

COMMENT ON COLUMN saga_instances.current_step IS
    '0=not started, 1=CREATE_ACCOUNT done, 2=KYC done,
     3=KEYCLOAK_USER done, 4=EMAIL sent';

COMMENT ON COLUMN saga_instances.state IS
    'See SagaState enum for all possible values.
     Terminal: COMPLETED, FAILED, COMPENSATION_FAILED';

COMMENT ON COLUMN saga_instances.failure_step IS
    'Which step (1-4) caused the saga to fail.
     Used by CompensationEngine to determine compensation scope.';
