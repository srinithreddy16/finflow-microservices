CREATE TABLE reports (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    name VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    report_type VARCHAR(50) NOT NULL,
    report_format VARCHAR(10) NOT NULL DEFAULT 'PDF',
    s3_key VARCHAR(500),
    s3_bucket VARCHAR(255),
    file_size_bytes BIGINT,
    from_date DATE,
    to_date DATE,
    error_message TEXT,
    correlation_id VARCHAR(100),
    requested_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    generated_at TIMESTAMP,
    CONSTRAINT chk_report_status CHECK (
        status IN ('PENDING','GENERATING','READY','FAILED')),
    CONSTRAINT chk_report_type CHECK (
        report_type IN ('TRANSACTION_HISTORY','ANALYTICS_SUMMARY','FRAUD_REPORT')),
    CONSTRAINT chk_report_format CHECK (
        report_format IN ('PDF','CSV'))
);

CREATE INDEX idx_reports_account_id
    ON reports(account_id);
CREATE INDEX idx_reports_status
    ON reports(status);
CREATE INDEX idx_reports_account_status
    ON reports(account_id, status);
CREATE INDEX idx_reports_created_at
    ON reports(created_at DESC);
CREATE INDEX idx_reports_report_type
    ON reports(account_id, report_type, status);

COMMENT ON TABLE reports IS
    'Report generation requests and metadata.
     Actual report files are stored in AWS S3.
     s3_key stores the S3 object key used to generate presigned URLs.
     Presigned download URLs are generated on-demand — NOT stored here.';

COMMENT ON COLUMN reports.s3_key IS
    'S3 object key format: reports/{accountId}/{reportType}/{reportId}.{ext}
     Example: reports/acc-001/transaction_history/abc-123.pdf';

COMMENT ON COLUMN reports.generated_at IS
    'When report generation completed (status changed to READY or FAILED).
     Presigned URL expires 1 hour after this timestamp.';
