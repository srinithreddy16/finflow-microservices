CREATE TABLE notification_logs (
    id VARCHAR(36) PRIMARY KEY,
    recipient_email VARCHAR(255),
    recipient_phone VARCHAR(20),
    account_id VARCHAR(36),
    channel VARCHAR(10) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    message_body TEXT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    event_type VARCHAR(50),
    reference_id VARCHAR(100),
    error_message TEXT,
    correlation_id VARCHAR(100),
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    CONSTRAINT chk_channel CHECK (channel IN ('EMAIL', 'SMS', 'PUSH')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING','SENT','FAILED','SKIPPED'))
);

CREATE INDEX idx_notif_account_id
    ON notification_logs(account_id);
CREATE INDEX idx_notif_status
    ON notification_logs(status);
CREATE INDEX idx_notif_event_type
    ON notification_logs(event_type);
CREATE INDEX idx_notif_created_at
    ON notification_logs(created_at DESC);
CREATE INDEX idx_notif_reference_id
    ON notification_logs(reference_id);
