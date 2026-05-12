# FinFlow — Cloud-Native Financial Operations Platform

> A production-grade B2B payment processing platform built with Java 21, Spring Boot 3.3, and AWS ECS — implementing microservices, event sourcing, saga patterns, gRPC, Kafka, RabbitMQ, and GraphQL.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303a?logo=gradle&logoColor=white)](https://gradle.org/)
[![AWS ECS](https://img.shields.io/badge/AWS-ECS%20Fargate-232F3E?logo=amazonaws&logoColor=white)](https://aws.amazon.com/ecs/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Build](https://img.shields.io/badge/Build-Passing-success)](https://github.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Table of Contents

1. [Project header](#finflow--cloud-native-financial-operations-platform)
2. [What is FinFlow](#section-3--what-is-finflow)
3. [Architecture overview](#section-4--architecture-overview)
4. [Tech stack](#section-5--tech-stack)
5. [Services and ports](#section-6--services-and-ports)
6. [Key design patterns](#section-7--key-design-patterns)
7. [Core domain flows](#section-8--core-domain-flows)
8. [Project structure](#section-9--project-structure)
9. [Prerequisites](#section-10--prerequisites)
10. [Quick start (local development)](#section-11--quick-start-local-development)
11. [Running tests](#section-12--running-tests)
12. [Observability URLs](#section-13--observability-urls)
13. [Kafka topic reference](#section-14--kafka-topic-reference)
14. [RabbitMQ exchange reference](#section-15--rabbitmq-exchange-reference)
15. [Deployment](#section-16--deployment)
16. [Environment variables](#section-17--environment-variables)
17. [Contributing](#section-18--contributing)
18. [License](#section-19--license)

---

## SECTION 3 — WHAT IS FINFLOW

FinFlow is a **B2B financial operations platform**: it models accounts, money movement, risk, notifications, analytics, and reporting as a cohesive product rather than a loose collection of scripts. Teams use it to offer payment and account capabilities to their own customers while keeping compliance, auditability, and operational visibility first-class.

If you compare to the public ecosystem, FinFlow sits in the same problem space as **Stripe** (payments and money movement), **Plaid** (accounts and connectivity), and **Tableau**-style **analytics** — but it is designed as an **internal platform** you own end-to-end: APIs, data, deployment, and extensions live in your repository and your cloud account.

Four primary **actors** interact with the system: the **End Customer** (pays, receives funds, sees status), the **Business Admin** (configures tenants, users, limits), the **Finance Analyst** (reads aggregates, exports reports, investigates exceptions), and **Partner Systems** (webhooks, APIs, batch integrations).

The guiding **design principle** is responsiveness where it matters: the customer-facing path aims for a **fast synchronous response** (on the order of **~80ms** for a typical payment acceptance decision) while everything that can be deferred — ledger settlement, notifications, analytics rollups, long-running reports — runs **asynchronously** over Kafka, RabbitMQ, and background workers so the hot path stays small and predictable.

---

## SECTION 4 — ARCHITECTURE OVERVIEW

Traffic enters from the internet through an **Application Load Balancer (ALB)**. The ALB routes HTTP to **API Gateway (:8080)** for REST and operational traffic, and to **GraphQL Gateway (:8081)** for dashboard-style reads that aggregate analytics, reports, and account context.

```text
Internet → ALB → [API Gateway :8080]     → downstream REST services
              → [GraphQL Gateway :8081] → analytics / report / account (read model)
```

**ECS private subnet** (logical view of the Spring Boot services):

```text
┌─────────────────────────────────────────────────────────────┐
│  API Gateway :8080      GraphQL Gateway :8081               │
│  Account Service :8082  Transaction Service :8083           │
│  Payment Service :8084  Saga Orchestrator :8085             │
│  Fraud Detection :8086  Notification Service :8087          │
│  Analytics Service :8088  Report Service :8089              │
└─────────────────────────────────────────────────────────────┘
```

**Infrastructure**

| Component | Role |
|-----------|------|
| **Kafka (MSK)** | Event streaming: transactions, fraud-events, analytics, audit-log, notify-events |
| **RabbitMQ** | Saga commands/replies, payment choreography (`saga.commands`, `saga.replies`, `payment.events`) |
| **Redis (ElastiCache)** | Rate limiting, GraphQL/query cache, real-time counters |
| **PostgreSQL (RDS)** | **Nine** dedicated databases — strict **database-per-service** |
| **S3** | Stored PDF/CSV reports and presigned download URLs |

---

## SECTION 5 — TECH STACK

| Category | Technology | Purpose |
|----------|------------|---------|
| Language | Java 21 | Virtual threads, records, pattern matching |
| Framework | Spring Boot 3.3 | All 10 microservices |
| Build | Gradle Kotlin DSL | Multi-module build with version catalog |
| API Gateway | Spring Cloud Gateway | JWT validation, Redis rate limiting |
| GraphQL | Spring for GraphQL | Analytics dashboard; **DataLoader** avoids N+1 |
| gRPC | Protobuf + net.devh | Synchronous fraud check (Transaction → Fraud) |
| Messaging | Apache Kafka (MSK) | Async event streaming (5 topics) |
| Messaging | RabbitMQ | Saga coordination, payment choreography |
| Cache | Redis (ElastiCache) | Rate limiting, query cache, analytics counters |
| Database | PostgreSQL 16 (RDS) | 9 databases — strict database-per-service |
| Auth | Keycloak 24 | OAuth2, JWT, SSO, realm management |
| Containers | Docker + ECS Fargate | All 10 services containerized |
| Orchestration | Kubernetes + AWS ECS | Production deployment, HPA auto-scaling |
| IaC | AWS CDK (Java) | Infrastructure as code — 8 CDK stacks |
| CI/CD | GitHub Actions | Build, staging, prod pipelines |
| Tracing | Jaeger + OpenTelemetry | Distributed traces across all services |
| Metrics | Prometheus + Grafana | Dashboards — throughput, fraud, saga, JVM |
| Logging | ELK Stack | Centralized structured JSON logs |
| Resilience | Resilience4j | Circuit breaker on fraud gRPC call |

---

## SECTION 6 — SERVICES AND PORTS

| Service | Port | Role | Key pattern |
|---------|------|------|-------------|
| api-gateway | `:8080` | REST entry point | JWT filter, Redis rate limiter |
| graphql-gateway | `:8081` | Dashboard read API | DataLoader N+1 prevention |
| account-service | `:8082` | User + tenant management | Saga participant (onboarding) |
| transaction-service | `:8083` | Core financial domain | CQRS + event sourcing |
| payment-service | `:8084` | Ledger + settlement | Choreography saga participant |
| saga-orchestrator-service | `:8085` | Onboarding coordinator | Orchestration saga “brain” |
| fraud-detection-service | `:8086` / `:9001` | Fraud scoring | gRPC server; Resilience4j CB |
| notification-service | `:8087` | Email / SMS / push | Kafka + RabbitMQ consumer |
| analytics-service | `:8088` | Real-time aggregation | Kafka consumer; Redis counters |
| report-service | `:8089` | PDF / CSV generation | PDFBox; S3 upload |
| keycloak | `:9090` (mapped) | Auth server | OAuth2, SSO, JWT |
| rabbitmq | `:5672` / `:15672` | Message broker | Saga + payment choreography |
| kafka | `:9092` | Event streaming | Multiple topics; consumer groups |
| redis | `:6379` | Cache + counters | Rate limit, query cache |
| prometheus | `:9093` (mapped) | Metrics | Scrapes `/actuator/prometheus` |
| grafana | `:3000` | Dashboards | FinFlow dashboards |
| jaeger | `:16686` | Distributed tracing | OpenTelemetry OTLP |
| elasticsearch | `:9200` | Log storage | `finflow-logs-*` indices |
| kibana | `:5601` | Log UI | Centralized log search |

---

## SECTION 7 — KEY DESIGN PATTERNS

**1. CQRS + Event Sourcing (transaction-service)**  
**What:** Commands mutate state by appending immutable domain events; reads rebuild from the event stream or projections. **Where:** `transaction-service` aggregate, event store, publishers. **Why:** Full audit trail, replay, and clear separation of write vs read models for compliance and scale-out reads.

**2. Orchestration Saga (account onboarding — saga-orchestrator)**  
**What:** A central coordinator owns the saga state machine and issues commands, then reacts to replies. **Where:** `saga-orchestrator-service` with RabbitMQ command/reply queues. **Why:** Onboarding is a long-lived, ordered workflow with compensation; a single orchestrator simplifies reasoning and failure handling.

**3. Choreography Saga (payment flow — RabbitMQ)**  
**What:** Services react to each other’s domain events without a central coordinator owning every transition. **Where:** `transaction-service` ↔ `payment-service` over `payment.events`. **Why:** Payment steps are naturally event-driven; choreography reduces coupling while keeping the flow observable.

**4. gRPC synchronous call (transaction → fraud-detection)**  
**What:** Binary, contract-first RPC with tight latency. **Where:** `transaction-service` client → `fraud-detection-service` server (`:9001`). **Why:** Fraud must be decided before commit; HTTP would add overhead and weaker typing for this hot path.

**5. DataLoader N+1 prevention (graphql-gateway)**  
**What:** Batch and cache related fetches per GraphQL request. **Where:** `graphql-gateway` DataLoader beans for account, transaction, fraud score. **Why:** Dashboard queries fan out; without batching, latency and load explode.

**6. Database per service (9 PostgreSQL instances)**  
**What:** Each bounded context has its own schema/database; no shared tables across services. **Where:** One RDS (or container) DB per service module. **Why:** Independent deploy, scaling, and failure isolation — core microservices tenet.

**7. Circuit breaker (Resilience4j on fraud gRPC call)**  
**What:** Fail fast or degrade when the fraud dependency is unhealthy. **Where:** `transaction-service` Resilience4j around the gRPC client. **Why:** Protect checkout latency when fraud is down; policy can allow/deny by business rules.

**8. Outbox / idempotency (duplicate transaction prevention)**  
**What:** Treat natural keys and command IDs so duplicate submits do not double-post. **Where:** `transaction-service` command handler checks aggregate existence before append. **Why:** Networks and UIs retry; finance systems must be exactly-once from the customer’s perspective.

---

## SECTION 8 — CORE DOMAIN FLOWS

### Flow 1: Account onboarding (orchestration saga)

1. **Client** → `api-gateway:8080` — `POST` onboarding request (JWT).  
2. **API Gateway** → `saga-orchestrator-service:8085` — forwards create saga.  
3. **Saga orchestrator** — persists `SagaInstance`, increments metrics, publishes **RabbitMQ** `saga.commands` (e.g. create account).  
4. **account-service:8082** — executes step, replies on `saga.replies`.  
5. **Saga orchestrator** — advances state (KYC, Keycloak user, welcome email steps similarly).  
6. On failure → **compensation** commands in reverse order; terminal state `FAILED` / `COMPENSATION_FAILED` as designed.  
7. **notification-service** may receive welcome / failure paths via messaging as configured.

### Flow 2: Payment processing (choreography saga)

1. **Client** → `api-gateway` → `transaction-service` — authorize / create payment intent.  
2. **transaction-service** — event append + **Kafka** publish (`transactions`, etc.).  
3. **payment-service** — consumes choreography events from **RabbitMQ** `payment.events`, updates ledger.  
4. **payment-service** — publishes completion / failure events.  
5. **transaction-service** / **notification-service** / **analytics-service** — react asynchronously to update projections, notify users, and refresh metrics.

### Flow 3: $500 payment end-to-end (~80 ms sync response)

1. **Client** → `api-gateway:8080` — `POST /api/transactions` with amount **$500**.  
2. **API Gateway** — JWT validation, optional Redis rate limit.  
3. **transaction-service** — **gRPC** `checkTransaction` → **fraud-detection-service:9001** (circuit breaker wrapped).  
4. **Fraud service** — rules + score; returns allow/deny quickly.  
5. **transaction-service** — if allowed, append **event-sourced** `TransactionCreated`, commit, return **202/200** style response to client (**~80 ms** target for the synchronous slice).  
6. **Async** — Kafka events to **analytics**, **notification**, and downstream **payment** choreography without blocking the HTTP response.

### Flow 4: Finance analyst GraphQL dashboard query

1. **Analyst** → `graphql-gateway:8081` / GraphiQL — authenticated GraphQL query (dashboard).  
2. **GraphQL Gateway** — resolves fields using **WebClient** to `analytics-service`, `report-service`, `account-service`.  
3. **DataLoader** — batches account / transaction / fraud-score keys to avoid N+1.  
4. **Optional Redis** — cached resolver payloads where configured.  
5. **Response** — single aggregated JSON for charts, report metadata, and account context.

---

## SECTION 9 — PROJECT STRUCTURE

```text
finflow/
├── common/                    ← shared Java types (no Spring)
├── proto/                     ← gRPC + Protobuf definitions
├── api-gateway/               ← Spring Cloud Gateway :8080
├── graphql-gateway/           ← Spring for GraphQL :8081
├── account-service/           ← :8082
├── transaction-service/       ← CQRS + Event Sourcing :8083
├── payment-service/           ← Ledger + Saga :8084
├── saga-orchestrator-service/ ← :8085
├── fraud-detection-service/   ← gRPC server :8086/:9001
├── notification-service/      ← :8087
├── analytics-service/         ← :8088
├── report-service/            ← PDF/CSV + S3 :8089
├── integration-tests/         ← Testcontainers end-to-end tests
├── infrastructure/
│   ├── docker/                ← docker-compose.yml + observability
│   ├── k8s/                   ← Kubernetes manifests
│   └── cdk/                   ← AWS CDK stacks (Java)
├── observability/
│   ├── prometheus/
│   ├── grafana/               ← dashboards
│   ├── jaeger/
│   └── elk/
└── .github/workflows/         ← CI + CD staging + CD prod
```

---

## SECTION 10 — PREREQUISITES

- **Java 21** (Eclipse Temurin recommended)  
- **Docker Desktop** (for Docker Compose and Testcontainers)  
- **Gradle 8.7** (or use `./gradlew` — wrapper is the source of truth)  
- **AWS CLI v2** (only if you deploy with CDK or AWS tooling)  
- **Node.js 18+** (only if you use the CDK CLI globally; Java CDK projects may still use `npx`)  
- **RAM:** at least **16 GB** recommended if you run the full local stack (all databases, Kafka, Redis, Keycloak, and services)

---

## SECTION 11 — QUICK START (LOCAL DEVELOPMENT)

**Step 1 — Clone the repository**

```bash
git clone https://github.com/your-username/finflow-cloud-native.git
cd finflow-cloud-native
```

**Step 2 — Copy environment variables**

```bash
cp .env.example .env
```

Edit `.env` with your local URLs, DB passwords, and AWS keys where applicable.

**Step 3 — Start infrastructure services**

From the repository root:

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d \
  keycloak-db keycloak kafka rabbitmq redis localstack \
  account-db transaction-db payment-db saga-db \
  fraud-db notification-db analytics-db report-db
```

**Step 4 — Wait for infrastructure to be healthy**

```bash
docker compose -f infrastructure/docker/docker-compose.yml ps
```

Wait until health checks show **healthy** (or equivalent) for dependencies your services need.

**Step 5 — Build all modules**

```bash
./gradlew build -x test --parallel
```

**Step 6 — Run a specific service locally**

```bash
./gradlew :transaction-service:bootRun
```

Repeat with `:account-service:bootRun`, etc., as needed. Point `SPRING_PROFILES_ACTIVE` and JDBC URLs at the Docker-hosted Postgres unless you use embedded/dev defaults.

**Step 7 — Start full observability stack**

```bash
docker compose \
  -f infrastructure/docker/docker-compose.yml \
  -f infrastructure/docker/docker-compose.observability.yml \
  up -d
```

Use `docker compose` (v2) or `docker-compose` (v1) depending on your installation.

---

## SECTION 12 — RUNNING TESTS

```bash
# Run all unit tests
./gradlew test
```

```bash
# Run tests for a specific service
./gradlew :transaction-service:test
```

```bash
# Run aggregate tests with report
./gradlew test --continue
```

```bash
# Run a specific test class
./gradlew :transaction-service:test \
  --tests "com.finflow.transaction.TransactionAggregateTest"
```

**Testcontainers:** Integration tests spin up **real** PostgreSQL, Kafka, RabbitMQ (and peers as configured) via Testcontainers. **Docker Desktop must be running** or those tests will fail to start containers.

---

## SECTION 13 — OBSERVABILITY URLS

After the observability compose profile is up (see Section 11, Step 7):

| Tool | URL | Credentials |
|------|-----|----------------|
| Grafana | http://localhost:3000 | `admin` / `finflow123` |
| Jaeger UI | http://localhost:16686 | — |
| Prometheus | http://localhost:9093 | — |
| Kibana | http://localhost:5601 | — |
| RabbitMQ Mgmt | http://localhost:15672 | `finflow` / `finflow` |
| Keycloak | http://localhost:9090 | `admin` / `admin` |
| GraphiQL | http://localhost:8081/graphiql | — (dev only) |
| Swagger UI | `http://localhost:8082/swagger-ui.html` (example) | — (per service; path may vary) |

---

## SECTION 14 — KAFKA TOPIC REFERENCE

| Topic | Producers | Consumers | Purpose |
|-------|-----------|-----------|---------|
| `transactions` | transaction-service | analytics-service | Transaction lifecycle events |
| `fraud-events` | fraud-detection | analytics, notification | Fraud scores and flags |
| `analytics` | transaction, payment | analytics-service | Payment / platform analytics |
| `audit-log` | transaction, payment, account | append-only consumers | Compliance / regulatory audit trail |
| `notify-events` | transaction, payment | notification-service | User notifications |

*(Exact topic names in code may use environment-specific prefixes — align consumers with `application.yml`.)*

---

## SECTION 15 — RABBITMQ EXCHANGE REFERENCE

| Exchange | Type | Routing key | Publisher → consumer |
|----------|------|-------------|----------------------|
| `saga.commands` | Direct | `account.commands` | saga-orchestrator → account-service |
| `saga.commands` | Direct | `saga.notify.welcome` | saga-orchestrator → notification-service |
| `saga.replies` | Direct | `saga.replies` | account-service → saga-orchestrator |
| `payment.events` | Topic | `payment.initiated` | transaction-service → payment-service |
| `payment.events` | Topic | `payment.completed` | payment-service → transaction-service |
| `payment.events` | Topic | `payment.failed` | payment-service → transaction-service, notification |

---

## SECTION 16 — DEPLOYMENT

### A) Docker Compose (local full stack)

```bash
docker compose -f infrastructure/docker/docker-compose.yml up
```

Add the observability file (see Section 11) when you need metrics, traces, and logs.

### B) Kubernetes (local with kind or minikube)

```bash
kubectl apply -f infrastructure/k8s/namespace.yml
kubectl apply -f infrastructure/k8s/configmaps/
kubectl apply -f infrastructure/k8s/deployments/
kubectl apply -f infrastructure/k8s/services/
kubectl apply -f infrastructure/k8s/ingress/
```

Adjust paths if your repo’s `infrastructure/k8s` layout differs.

### C) AWS CDK (production)

```bash
cd infrastructure/cdk
npm install -g aws-cdk
cdk bootstrap aws://ACCOUNT_ID/us-east-1
cdk deploy --all
```

Replace `ACCOUNT_ID` and region with your AWS account details.

### D) GitHub Actions CI/CD

- **CI** runs on every push and pull request (build and test).  
- **CD to staging:** merge to the `develop` branch (if configured).  
- **CD to production:** merge to `main`, often with a **manual approval** gate.  
- Configure required reviewers under **GitHub → Settings → Environments → `production` → Required reviewers**.

---

## SECTION 17 — ENVIRONMENT VARIABLES

The canonical list lives in **`.env.example`** at the repository root. Copy it to `.env` and tune for your machine or cluster.

**Highlights (one line each)**

| Group | Variable | Description |
|-------|----------|-------------|
| Keycloak | `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWKS_URI` | Realm issuer and JWKS for JWT validation |
| Keycloak | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD` | Admin console login |
| API Gateway | `REDIS_HOST`, `REDIS_PORT` | Redis for rate limiting / cache |
| Databases | `*_DB_URL`, `*_DB_USERNAME`, `*_DB_PASSWORD` | JDBC URLs and credentials per service |
| Fraud | `FRAUD_SERVICE_HOST`, `FRAUD_SERVICE_GRPC_PORT` | gRPC target for transaction-service |
| Reports | `AWS_S3_BUCKET_NAME`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | S3 report storage |
| Messaging | `KAFKA_BOOTSTRAP_SERVERS` | Kafka clients bootstrap |
| Messaging | `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | AMQP connection |

For every variable and sensible defaults, see **`.env.example`**.

---

## SECTION 18 — CONTRIBUTING

1. **Fork** the repository.  
2. **Branch:** `git checkout -b feature/your-feature`  
3. **Commit** using [Conventional Commits](https://www.conventionalcommits.org/), e.g. `feat(transaction-service): add idempotency key`  
4. **Push** and open a **Pull Request** against `develop`.  
5. **CI must pass** before merge.  
6. For coding standards and review expectations, see **`CONTRIBUTING.md`** (add it in-repo if not present yet).

---

## SECTION 19 — LICENSE

This project is licensed under the **MIT License** — see the **`LICENSE`** file in the repository root.
