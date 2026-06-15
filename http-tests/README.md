# FinFlow HTTP Tests

IntelliJ IDEA HTTP Client test files for all FinFlow endpoints.

## Files

| File | Service | Description |
|---|---|---|
| `auth.http` | Keycloak | Get JWT tokens |
| `health-checks.http` | All services | Verify all services are UP |
| `account-service.http` | Account Service :8082 | Tenant + account CRUD |
| `transaction-service.http` | Transaction Service :8083 | CQRS + fraud check |
| `payment-service.http` | Payment Service :8084 | Ledger + balance |
| `saga-orchestrator.http` | Saga Orchestrator :8085 | Onboarding saga |
| `analytics-service.http` | Analytics Service :8088 | Metrics + summaries |
| `report-service.http` | Report Service :8089 | PDF/CSV + S3 URLs |
| `graphql-gateway.http` | GraphQL Gateway :8081 | Dashboard queries |
| `api-gateway.http` | API Gateway :8080 | Routing + JWT + rate limit |
| `end-to-end-flow.http` | All services | Complete payment flow |

## Setup in IntelliJ

1. Open any `.http` file
2. Click the environment dropdown (top right) → select `local`
3. Run `auth.http` **Request [1]** first to get your token
4. The token is saved automatically to `{{token}}`
5. Run any other request — variables carry over between files

## Recommended Run Order

1. Start all services (see main README)
2. `health-checks.http` — verify all UP
3. `auth.http` [1] — get token
4. `account-service.http` [1] — create tenant → saves `{{tenantId}}`
5. `account-service.http` [6] — create account → saves `{{accountId}}`
6. `transaction-service.http` [1] — create transaction → saves `{{transactionId}}`
7. `payment-service.http` [1] — verify payment → saves `{{paymentId}}`
8. `payment-service.http` [5] — verify ledger (2 entries)
9. `analytics-service.http` [1] — check analytics (wait 15s)
10. `report-service.http` [1] — generate PDF report
11. `saga-orchestrator.http` [1] — start saga → saves `{{sagaId}}`
12. `saga-orchestrator.http` [2] — poll saga status
13. `graphql-gateway.http` [1] — dashboard query
14. `end-to-end-flow.http` — full automated flow

## Environment Variables

Defined in `http-client.env.json`.
Sensitive values go in `http-client.private.env.json` (gitignored).

| Variable | Description |
|---|---|
| `token` | JWT access token (set by auth.http) |
| `accountId` | Set by account-service.http [6] |
| `tenantId` | Set by account-service.http [1] |
| `transactionId` | Set by transaction-service.http [1] |
| `paymentId` | Set by payment-service.http [1] |
| `sagaId` | Set by saga-orchestrator.http [1] |
| `reportId` | Set by report-service.http [1] |

## Notes

- Analytics data is populated asynchronously via Kafka.
  Wait 10-15 seconds after creating transactions before querying analytics.
- Payments are created asynchronously via RabbitMQ choreography saga.
  Wait 2-3 seconds after creating a transaction before querying payment.
- Saga steps are asynchronous — poll the status endpoint every 2 seconds.
- Reports are generated synchronously — response returns after generation.
