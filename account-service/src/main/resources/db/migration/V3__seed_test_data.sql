-- Insert a test tenant
INSERT INTO tenants (id, name, slug, owner_email, status)
VALUES (
    'tenant-001',
    'FinFlow Demo Corp',
    'finflow-demo',
    'admin@finflow.com',
    'ACTIVE'
);

-- Insert a test account
INSERT INTO accounts (id, email, first_name, last_name, phone_number, 
                      status, tenant_id, kyc_verified)
VALUES (
    'account-001',
    'customer@finflow.com',
    'Test',
    'Customer',
    '+1234567890',
    'ACTIVE',
    'tenant-001',
    TRUE
);
