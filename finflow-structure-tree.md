finflow/
│
├── build.gradle.kts                                    ← root build (allprojects config)
├── settings.gradle.kts                                 ← includes all subprojects
├── gradle.properties                                   ← JVM args, parallel builds, caching
│
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties                   ← Gradle 8.7
│   └── libs.versions.toml                              ← version catalog (single source of truth)
│
├── .github/
│   └── workflows/
│       ├── ci.yml                                      ← build + test all modules on every PR
│       ├── cd-staging.yml                              ← deploy to staging on merge to develop
│       └── cd-prod.yml                                 ← deploy to prod on merge to main
│
├── .env.example
├── .gitignore
├── README.md
└── CONTRIBUTING.md
│
│
├── ── SHARED MODULES ───────────────────────────────────────────────────────────
│
├── common/                                             ← shared Java types (no Spring dependency)
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/finflow/common/
│       │   ├── event/
│       │   │   ├── DomainEvent.java                    ← base interface every domain event implements
│       │   │   └── EventMetadata.java                  ← eventId, correlationId, traceId, occurredOn
│       │   ├── exception/
│       │   │   ├── FinFlowException.java               ← base RuntimeException with ErrorCode + HttpStatus
│       │   │   ├── ErrorCode.java                      ← all error codes across every service
│       │   │   └── ErrorResponse.java                  ← standard API error body returned by every service
│       │   ├── dto/
│       │   │   └── PagedResponse.java                  ← generic paginated wrapper over Spring Page<T>
│       │   └── util/
│       │       ├── IdGenerator.java                    ← UUID generator for entity IDs + correlation IDs
│       │       └── DateTimeUtils.java                  ← UTC timestamp formatting helpers
│       └── test/java/com/finflow/common/
│           └── IdGeneratorTest.java
│
├── proto/                                              ← all .proto files → compiled to Java stubs
│   ├── build.gradle.kts                               ← protobuf plugin compiles .proto → Java
│   └── src/main/proto/
│       ├── fraud_check.proto                           ← FraudCheckService: CheckTransaction RPC
│       ├── transaction_event.proto                     ← TransactionEvent Kafka message schema
│       └── payment_event.proto                         ← PaymentEvent Kafka message schema
│
│
├── ── SERVICES ─────────────────────────────────────────────────────────────────
│
├── api-gateway/                                        ← Spring Cloud Gateway (WebFlux) · :8080
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/finflow/gateway/
│       │   │   ├── ApiGatewayApplication.java
│       │   │   ├── config/
│       │   │   │   ├── GatewayConfig.java              ← route definitions to all downstream services
│       │   │   │   ├── RedisRateLimiterConfig.java      ← per-user rate limiting via ElastiCache Redis
│       │   │   │   └── SecurityConfig.java              ← OAuth2 resource server, Keycloak JWKS URI
│       │   │   ├── filter/
│       │   │   │   ├── KeycloakJwtFilter.java           ← validates Bearer token with Keycloak /userinfo
│       │   │   │   ├── RequestLoggingFilter.java        ← logs method, path, status, response time
│       │   │   │   └── TracingFilter.java               ← injects X-Trace-Id + X-Correlation-Id headers
│       │   │   ├── fallback/
│       │   │   │   └── GatewayFallbackController.java   ← circuit breaker fallback responses (503)
│       │   │   └── exception/
│       │   │       └── GatewayExceptionHandler.java     ← maps 401/403/429/503 to structured ErrorResponse
│       │   └── resources/
│       │       ├── application.yml                      ← routes, Redis, Keycloak JWKS URI config
│       │       └── application-prod.yml                 ← AWS service DNS overrides for ECS
│       └── test/java/com/finflow/gateway/
│           ├── GatewayRoutingTest.java                  ← route resolution tests (WebTestClient)
│           └── RateLimiterTest.java                     ← Redis rate limit behaviour tests
│
├── graphql-gateway/                                    ← Spring for GraphQL · :8081
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/finflow/graphql/
│       │   │   ├── GraphqlGatewayApplication.java
│       │   │   ├── config/
│       │   │   │   ├── GraphqlSecurityConfig.java       ← JWT validation for POST /graphql endpoint
│       │   │   │   ├── DataLoaderConfig.java            ← registers all DataLoader beans (N+1 prevention)
│       │   │   │   └── GraphqlConfig.java               ← schema stitching, error handling
│       │   │   ├── resolver/
│       │   │   │   ├── TransactionQueryResolver.java    ← Query: transactions, summaries by date/currency
│       │   │   │   ├── AnalyticsQueryResolver.java      ← Query: aggregates → calls analytics-service REST
│       │   │   │   ├── ReportQueryResolver.java         ← Query: report list, URLs → calls report-service REST
│       │   │   │   ├── AccountQueryResolver.java        ← Query: profiles → calls account-service REST
│       │   │   │   └── SubscriptionResolver.java        ← WebSocket subscription: live fraud alert feed
│       │   │   ├── dataloader/
│       │   │   │   ├── AccountDataLoader.java           ← batches N account lookups into 1 DB call
│       │   │   │   ├── TransactionDataLoader.java       ← batches transaction lookups
│       │   │   │   └── FraudScoreDataLoader.java        ← batches fraud score lookups
│       │   │   ├── client/
│       │   │   │   ├── AnalyticsServiceClient.java      ← REST client → analytics-service :8088
│       │   │   │   ├── ReportServiceClient.java         ← REST client → report-service :8089
│       │   │   │   └── AccountServiceClient.java        ← REST client → account-service :8082
│       │   │   ├── cache/
│       │   │   │   └── GraphqlRedisCache.java           ← caches resolver results in Redis
│       │   │   └── scalar/
│       │   │       └── CustomScalars.java               ← Date, DateTime, BigDecimal scalars
│       │   └── resources/
│       │       ├── application.yml
│       │       └── graphql/
│       │           └── schema.graphqls                  ← full GraphQL schema (queries, mutations, subs)
│       └── test/java/com/finflow/graphql/
│           ├── TransactionQueryResolverTest.java
│           ├── AnalyticsQueryResolverTest.java
│           └── DataLoaderTest.java                      ← N+1 prevention verification
│
├── account-service/                                    ← user + tenant management · :8082
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/finflow/account/
│       │   │   ├── AccountServiceApplication.java
│       │   │   ├── config/
│       │   │   │   ├── SecurityConfig.java
│       │   │   │   ├── KeycloakAdminConfig.java         ← Keycloak Admin REST client bean
│       │   │   │   ├── RabbitMQConfig.java              ← saga command/reply queue bindings  ✚ ADDED
│       │   │   │   └── KafkaProducerConfig.java         ← Kafka producer bean for audit-log  ✚ ADDED
│       │   │   ├── controller/
│       │   │   │   ├── AccountController.java           ← POST /accounts  GET /accounts/{id}
│       │   │   │   └── TenantController.java            ← POST /tenants  GET /tenants/{id}
│       │   │   ├── service/
│       │   │   │   ├── AccountService.java              ← create, update, deactivate logic
│       │   │   │   ├── TenantService.java
│       │   │   │   └── KycService.java                  ← delegates KYC check to fraud-service
│       │   │   ├── keycloak/
│       │   │   │   └── KeycloakAdminClient.java         ← creates/deletes users in Keycloak realm
│       │   │   ├── saga/                               ← ORCHESTRATION SAGA participant
│       │   │   │   ├── AccountSagaParticipant.java      ← consumes saga.commands from RabbitMQ
│       │   │   │   ├── AccountCompensationHandler.java  ← consumes ROLLBACK cmd, reverses account
│       │   │   │   └── SagaReplyPublisher.java          ← publishes step result to saga.replies  ✚ ADDED
│       │   │   ├── kafka/
│       │   │   │   └── AccountEventPublisher.java       ← publishes audit-log events to Kafka  ✚ ADDED
│       │   │   ├── repository/
│       │   │   │   ├── AccountRepository.java
│       │   │   │   └── TenantRepository.java
│       │   │   ├── model/
│       │   │   │   ├── Account.java                     ← JPA entity (@Entity @Data @Builder)
│       │   │   │   ├── Tenant.java
│       │   │   │   └── AccountStatus.java               ← PENDING, ACTIVE, SUSPENDED, CLOSED
│       │   │   ├── dto/
│       │   │   │   ├── AccountRequestDto.java           ← Java record + @Valid Bean Validation
│       │   │   │   ├── AccountResponseDto.java
│       │   │   │   └── TenantDto.java
│       │   │   ├── mapper/
│       │   │   │   └── AccountMapper.java               ← MapStruct @Mapper interface
│       │   │   └── exception/
│       │   │       ├── AccountNotFoundException.java
│       │   │       ├── DuplicateAccountException.java
│       │   │       └── GlobalExceptionHandler.java      ← @RestControllerAdvice
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-prod.yml
│       │       └── db/migration/
│       │           ├── V1__create_accounts_table.sql    ← Flyway migration
│       │           └── V2__create_tenants_table.sql
│       └── test/java/com/finflow/account/
│           ├── AccountServiceTest.java                  ← unit tests (Mockito)
│           ├── AccountControllerTest.java               ← MockMvc slice tests
│           ├── AccountSagaParticipantTest.java
│           └── AccountIntegrationTest.java              ← Testcontainers: Postgres + RabbitMQ + Kafka
│
├── transaction-service/                                ← CQRS + Event Sourcing · :8083
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/finflow/transaction/
│       │   │   ├── TransactionServiceApplication.java
│       │   │   ├── config/
│       │   │   │   ├── SecurityConfig.java
│       │   │   │   ├── KafkaProducerConfig.java         ← Kafka producer beans (protobuf serializer)
│       │   │   │   └── RabbitMQConfig.java              ← payment event exchange + binding definitions
│       │   │   ├── command/                            ← CQRS write side
│       │   │   │   ├── CreateTransactionCommand.java    ← Java record (immutable command object)
│       │   │   │   ├── UpdateTransactionCommand.java
│       │   │   │   ├── ReverseTransactionCommand.java
│       │   │   │   └── TransactionCommandHandler.java   ← validates + dispatches to aggregate
│       │   │   ├── aggregate/
│       │   │   │   └── TransactionAggregate.java        ← applies events, enforces invariants
│       │   │   ├── event/                              ← Event Sourcing
│       │   │   │   ├── TransactionEvent.java            ← abstract base class for all TX events
│       │   │   │   ├── TransactionCreatedEvent.java
│       │   │   │   ├── TransactionCompletedEvent.java
│       │   │   │   ├── TransactionReversedEvent.java
│       │   │   │   ├── TransactionFailedEvent.java
│       │   │   │   ├── EventStore.java                  ← interface: append(event) + findByAggregateId()
│       │   │   │   ├── EventStoreImpl.java              ← appends immutable rows to event_store table
│       │   │   │   └── EventStoreRepository.java        ← JPA repo for event_store table
│       │   │   ├── query/                              ← CQRS read side
│       │   │   │   ├── GetTransactionQuery.java
│       │   │   │   ├── GetTransactionsByAccountQuery.java
│       │   │   │   └── TransactionQueryHandler.java     ← reads projection table via Redis cache
│       │   │   ├── projection/
│       │   │   │   ├── TransactionProjection.java       ← flat read model JPA entity
│       │   │   │   ├── TransactionProjectionUpdater.java← listens to events, rebuilds projection
│       │   │   │   └── ProjectionRepository.java
│       │   │   ├── saga/                               ← CHOREOGRAPHY SAGA initiator
│       │   │   │   ├── PaymentSagaInitiator.java        ← publishes PaymentInitiated to RabbitMQ
│       │   │   │   └── PaymentCompensationHandler.java  ← consumes PaymentFailed, reverses TX
│       │   │   ├── grpc/
│       │   │   │   └── FraudCheckGrpcClient.java        ← blocking stub → fraud-service:9001
│       │   │   ├── kafka/
│       │   │   │   └── TransactionEventPublisher.java   ← publishes to: transactions, analytics,
│       │   │   │                                           audit-log, notify-events
│       │   │   ├── controller/
│       │   │   │   └── TransactionController.java       ← POST /transactions  GET /transactions/{id}
│       │   │   ├── model/
│       │   │   │   ├── Transaction.java                 ← JPA projection entity
│       │   │   │   └── TransactionStatus.java           ← PENDING, COMPLETED, FAILED, REVERSED
│       │   │   ├── dto/
│       │   │   │   ├── TransactionRequestDto.java
│       │   │   │   └── TransactionResponseDto.java
│       │   │   ├── mapper/
│       │   │   │   └── TransactionMapper.java           ← MapStruct
│       │   │   └── exception/
│       │   │       ├── TransactionNotFoundException.java
│       │   │       ├── InsufficientFundsException.java
│       │   │       └── GlobalExceptionHandler.java
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-prod.yml
│       │       └── db/migration/
│       │           ├── V1__create_event_store_table.sql
│       │           └── V2__create_transaction_projection_table.sql
│       └── test/java/com/finflow/transaction/
│           ├── TransactionCommandHandlerTest.java
│           ├── TransactionAggregateTest.java            ← event sourcing unit tests
│           ├── EventStoreTest.java
│           ├── ProjectionUpdaterTest.java
│           ├── PaymentSagaInitiatorTest.java
│           └── TransactionIntegrationTest.java          ← Testcontainers: Postgres + Kafka + RabbitMQ
│
├── payment-service/                                    ← ledger + double-entry bookkeeping · :8084
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/finflow/payment/
│       │   │   ├── PaymentServiceApplication.java
│       │   │   ├── config/
│       │   │   │   ├── SecurityConfig.java
│       │   │   │   ├── RabbitMQConfig.java              ← payment event exchange bindings
│       │   │   │   └── KafkaProducerConfig.java         ← Kafka producer for analytics/audit  ✚ ADDED
│       │   │   ├── saga/                               ← CHOREOGRAPHY SAGA participant
│       │   │   │   ├── PaymentSagaHandler.java          ← consumes PaymentInitiated, processes ledger
│       │   │   │   └── PaymentEventPublisher.java       ← publishes PaymentCompleted/Failed to RabbitMQ
│       │   │   ├── kafka/
│       │   │   │   └── PaymentEventKafkaPublisher.java  ← publishes to analytics, audit-log,  ✚ ADDED
│       │   │   │                                           notify-events topics
│       │   │   ├── service/
│       │   │   │   ├── PaymentService.java              ← debit/credit logic
│       │   │   │   └── LedgerService.java               ← double-entry bookkeeping
│       │   │   ├── controller/
│       │   │   │   └── PaymentController.java           ← GET /payments/{id}
│       │   │   ├── repository/
│       │   │   │   ├── PaymentRepository.java
│       │   │   │   └── LedgerRepository.java
│       │   │   ├── model/
│       │   │   │   ├── Payment.java
│       │   │   │   ├── LedgerEntry.java
│       │   │   │   └── PaymentStatus.java               ← PENDING, COMPLETED, FAILED, REFUNDED
│       │   │   ├── dto/
│       │   │   │   ├── PaymentResponseDto.java
│       │   │   │   └── LedgerEntryDto.java
│       │   │   └── exception/
│       │   │       ├── PaymentNotFoundException.java
│       │   │       ├── InsufficientBalanceException.java
│       │   │       └── GlobalExceptionHandler.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           ├── V1__create_payments_table.sql
│       │           └── V2__create_ledger_entries_table.sql
│       └── test/java/com/finflow/payment/
│           ├── PaymentServiceTest.java
│           ├── LedgerServiceTest.java
│           ├── PaymentSagaHandlerTest.java
│           └── PaymentIntegrationTest.java
│
├── saga-orchestrator-service/                          ← ORCHESTRATION SAGA brain · :8085
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/finflow/saga/
│       │   │   ├── SagaOrchestratorApplication.java
│       │   │   ├── config/
│       │   │   │   └── RabbitMQConfig.java              ← all saga exchange/queue/binding definitions
│       │   │   ├── orchestrator/
│       │   │   │   ├── OnboardingSagaOrchestrator.java  ← state machine: sends cmds, awaits replies
│       │   │   │   └── SagaReplyListener.java           ← consumes saga.replies from RabbitMQ
│       │   │   ├── steps/
│       │   │   │   ├── SagaStep.java                    ← interface: execute() + compensate()
│       │   │   │   ├── CreateAccountStep.java           ← sends CREATE_ACCOUNT command
│       │   │   │   ├── KycCheckStep.java                ← sends KYC_CHECK command
│       │   │   │   ├── CreateKeycloakUserStep.java      ← sends CREATE_KEYCLOAK_USER command
│       │   │   │   └── SendWelcomeEmailStep.java        ← sends SEND_WELCOME_EMAIL command
│       │   │   ├── compensation/
│       │   │   │   └── CompensationEngine.java          ← executes compensate() in reverse step order
│       │   │   ├── state/
│       │   │   │   ├── SagaState.java                   ← STARTED, IN_PROGRESS, COMPLETED,
│       │   │   │   │                                       COMPENSATING, FAILED
│       │   │   │   ├── SagaInstance.java                ← JPA entity persisting saga progress
│       │   │   │   └── SagaInstanceRepository.java
│       │   │   └── exception/
│       │   │       └── SagaExecutionException.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           └── V1__create_saga_instances_table.sql
│       └── test/java/com/finflow/saga/
│           ├── OnboardingSagaOrchestratorTest.java
│           ├── CompensationEngineTest.java
│           └── SagaIntegrationTest.java                 ← full saga: happy path + compensation path
│
├── fraud-detection-service/                            ← gRPC server + Kafka + Resilience4j · :8086/:9001
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/finflow/fraud/
│       │   │   ├── FraudDetectionApplication.java
│       │   │   ├── config/
│       │   │   │   ├── SecurityConfig.java
│       │   │   │   ├── GrpcServerConfig.java            ← gRPC server port 9001 + TLS config
│       │   │   │   ├── Resilience4jConfig.java          ← circuit breaker + retry + timeout beans
│       │   │   │   └── KafkaProducerConfig.java         ← Kafka producer for fraud-events  ✚ ADDED
│       │   │   ├── grpc/
│       │   │   │   └── FraudCheckGrpcService.java       ← @GrpcService: sync check from transaction-svc
│       │   │   ├── kafka/
│       │   │   │   ├── FraudEventConsumer.java          ← consumes fraud-events (async alerts)
│       │   │   │   └── FraudEventPublisher.java         ← publishes fraud-events after scoring  ✚ ADDED
│       │   │   ├── rules/
│       │   │   │   ├── FraudRuleEngine.java             ← evaluates all rules against transaction
│       │   │   │   ├── AmountThresholdRule.java         ← flag if amount > $10,000
│       │   │   │   ├── VelocityRule.java                ← flag if > 5 TXs in 60 seconds
│       │   │   │   └── GeolocationRule.java             ← flag if country mismatch
│       │   │   ├── service/
│       │   │   │   └── FraudScoringService.java         ← aggregates rules into 0-100 score
│       │   │   ├── repository/
│       │   │   │   └── FraudRecordRepository.java
│       │   │   ├── model/
│       │   │   │   ├── FraudRecord.java
│       │   │   │   └── FraudScore.java
│       │   │   └── exception/
│       │   │       └── FraudServiceException.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           └── V1__create_fraud_records_table.sql
│       └── test/java/com/finflow/fraud/
│           ├── FraudRuleEngineTest.java
│           ├── FraudScoringServiceTest.java
│           ├── FraudCheckGrpcServiceTest.java           ← gRPC unit test (in-process channel)
│           └── FraudIntegrationTest.java
│
├── notification-service/                               ← Kafka + RabbitMQ consumer · :8087
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/finflow/notification/
│       │   │   ├── NotificationServiceApplication.java
│       │   │   ├── config/
│       │   │   │   ├── KafkaConsumerConfig.java
│       │   │   │   └── RabbitMQConfig.java
│       │   │   ├── consumer/
│       │   │   │   ├── KafkaNotificationConsumer.java       ← consumes: fraud-events, notify-events
│       │   │   │   └── RabbitMQNotificationConsumer.java    ← consumes: saga.commands (notify.welcome),
│       │   │   │                                               notify.commands
│       │   │   ├── service/
│       │   │   │   ├── NotificationService.java             ← routes to correct channel handler
│       │   │   │   ├── EmailService.java                    ← AWS SES integration
│       │   │   │   ├── SmsService.java                      ← AWS SNS / Twilio integration
│       │   │   │   └── PushService.java                     ← FCM push notifications
│       │   │   ├── template/
│       │   │   │   ├── WelcomeEmailTemplate.java
│       │   │   │   ├── PaymentReceiptTemplate.java
│       │   │   │   ├── PaymentFailedTemplate.java
│       │   │   │   └── FraudAlertTemplate.java
│       │   │   ├── repository/
│       │   │   │   └── NotificationLogRepository.java
│       │   │   └── model/
│       │   │       ├── NotificationLog.java
│       │   │       └── NotificationChannel.java             ← EMAIL, SMS, PUSH
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           └── V1__create_notification_logs_table.sql
│       └── test/java/com/finflow/notification/
│           ├── NotificationServiceTest.java
│           ├── KafkaNotificationConsumerTest.java
│           └── RabbitMQNotificationConsumerTest.java
│
├── analytics-service/                                  ← Kafka consumer + Redis counters · :8088
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/finflow/analytics/
│       │   │   ├── AnalyticsServiceApplication.java
│       │   │   ├── config/
│       │   │   │   ├── KafkaConsumerConfig.java
│       │   │   │   └── RedisConfig.java
│       │   │   ├── consumer/
│       │   │   │   └── AnalyticsEventConsumer.java      ← consumes: transactions, fraud-events, analytics
│       │   │   ├── service/
│       │   │   │   ├── AnalyticsAggregatorService.java  ← aggregates events into daily/currency metrics
│       │   │   │   └── AnalyticsQueryService.java       ← query interface called by controller
│       │   │   ├── cache/
│       │   │   │   └── AnalyticsRedisCache.java         ← Redis counters: daily volume, TX count
│       │   │   ├── controller/
│       │   │   │   └── AnalyticsController.java         ← GET /analytics/summary (called by GraphQL GW)
│       │   │   ├── repository/
│       │   │   │   └── AnalyticsReadModelRepository.java
│       │   │   ├── model/
│       │   │   │   ├── AnalyticsReadModel.java          ← projection: aggregated per day + currency
│       │   │   │   └── TransactionMetrics.java
│       │   │   └── dto/
│       │   │       └── AnalyticsSummaryDto.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           └── V1__create_analytics_read_model_table.sql
│       └── test/java/com/finflow/analytics/
│           ├── AnalyticsAggregatorServiceTest.java
│           ├── AnalyticsEventConsumerTest.java
│           └── AnalyticsIntegrationTest.java            ← Testcontainers: Kafka + Redis + Postgres
│
├── report-service/                                     ← PDF/CSV generation + S3 · :8089
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/finflow/report/
│       │   │   ├── ReportServiceApplication.java
│       │   │   ├── config/
│       │   │   │   ├── SecurityConfig.java
│       │   │   │   └── S3Config.java                    ← AWS S3 client bean
│       │   │   ├── controller/
│       │   │   │   └── ReportController.java            ← POST /reports  GET /reports/{id}
│       │   │   │                                           (called by GraphQL gateway)
│       │   │   ├── service/
│       │   │   │   ├── ReportService.java               ← orchestrates generation + upload
│       │   │   │   ├── PdfGeneratorService.java         ← Apache PDFBox PDF generation
│       │   │   │   ├── CsvGeneratorService.java         ← OpenCSV export
│       │   │   │   └── S3UploadService.java             ← upload + presigned URL (1hr expiry)
│       │   │   ├── repository/
│       │   │   │   └── ReportRepository.java
│       │   │   ├── model/
│       │   │   │   ├── Report.java
│       │   │   │   └── ReportStatus.java                ← PENDING, GENERATING, READY, FAILED
│       │   │   ├── dto/
│       │   │   │   ├── ReportRequestDto.java
│       │   │   │   └── ReportResponseDto.java
│       │   │   └── exception/
│       │   │       └── ReportGenerationException.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           └── V1__create_reports_table.sql
│       └── test/java/com/finflow/report/
│           ├── ReportServiceTest.java
│           ├── PdfGeneratorServiceTest.java
│           └── ReportIntegrationTest.java               ← Testcontainers + LocalStack S3
│
│
├── ── INFRASTRUCTURE ───────────────────────────────────────────────────────────
│
├── infrastructure/
│   ├── docker/
│   │   ├── docker-compose.yml                          ← full local stack (all 10 services +
│   │   │                                                  9× Postgres + Kafka + ZooKeeper +
│   │   │                                                  RabbitMQ + Redis + Keycloak)
│   │   ├── docker-compose.observability.yml            ← Prometheus + Grafana + Jaeger + ELK
│   │   └── docker-compose.override.yml                 ← local dev overrides (debug ports)
│   │
│   ├── k8s/
│   │   ├── namespace.yml                               ← finflow Kubernetes namespace
│   │   ├── configmaps/
│   │   │   ├── api-gateway-config.yml
│   │   │   ├── graphql-gateway-config.yml
│   │   │   ├── account-service-config.yml
│   │   │   ├── transaction-service-config.yml
│   │   │   ├── payment-service-config.yml
│   │   │   ├── saga-orchestrator-config.yml
│   │   │   ├── fraud-detection-config.yml
│   │   │   ├── notification-service-config.yml
│   │   │   ├── analytics-service-config.yml
│   │   │   └── report-service-config.yml
│   │   ├── secrets/
│   │   │   ├── db-credentials.yml
│   │   │   ├── keycloak-secret.yml
│   │   │   ├── kafka-credentials.yml
│   │   │   └── rabbitmq-credentials.yml
│   │   ├── deployments/
│   │   │   ├── api-gateway-deployment.yml
│   │   │   ├── graphql-gateway-deployment.yml
│   │   │   ├── account-service-deployment.yml
│   │   │   ├── transaction-service-deployment.yml
│   │   │   ├── payment-service-deployment.yml
│   │   │   ├── saga-orchestrator-deployment.yml
│   │   │   ├── fraud-detection-deployment.yml
│   │   │   ├── notification-service-deployment.yml
│   │   │   ├── analytics-service-deployment.yml
│   │   │   └── report-service-deployment.yml
│   │   ├── services/
│   │   │   ├── api-gateway-service.yml                 ← LoadBalancer (public)
│   │   │   ├── graphql-gateway-service.yml             ← LoadBalancer (public)
│   │   │   ├── account-service-service.yml             ← ClusterIP (internal)
│   │   │   ├── transaction-service-service.yml
│   │   │   ├── payment-service-service.yml
│   │   │   ├── saga-orchestrator-service.yml
│   │   │   ├── fraud-detection-service.yml
│   │   │   ├── notification-service-service.yml
│   │   │   ├── analytics-service-service.yml
│   │   │   └── report-service-service.yml
│   │   ├── hpa/
│   │   │   ├── transaction-service-hpa.yml             ← auto-scale on CPU > 70%
│   │   │   └── payment-service-hpa.yml
│   │   └── ingress/
│   │       └── finflow-ingress.yml                     ← /api/* → api-gateway
│   │                                                      /graphql → graphql-gateway
│   │
│   └── cdk/                                            ← AWS CDK (Java)
│       ├── build.gradle.kts
│       └── src/main/java/com/finflow/infra/
│           ├── FinFlowApp.java                         ← CDK App entry point
│           ├── FinFlowStack.java                       ← main stack: VPC + ECS cluster + ALB
│           └── stacks/
│               ├── NetworkStack.java                   ← VPC, public/private subnets, NAT
│               ├── DatabaseStack.java                  ← 9× RDS PostgreSQL (one per service)
│               ├── MessagingStack.java                 ← MSK Kafka cluster + RabbitMQ ECS task
│               ├── CacheStack.java                     ← ElastiCache Redis cluster
│               ├── StorageStack.java                   ← S3 bucket (reports + exports)
│               ├── EcsClusterStack.java                ← ECS cluster + Fargate per service
│               ├── KeycloakStack.java                  ← Keycloak ECS task + RDS backend
│               └── ObservabilityStack.java             ← Prometheus + Grafana + Jaeger ECS tasks
│
│
├── ── INTEGRATION TESTS ────────────────────────────────────────────────────────
│
├── integration-tests/
│   ├── build.gradle.kts
│   └── src/test/java/com/finflow/integration/
│       ├── config/
│       │   └── IntegrationTestConfig.java              ← Testcontainers: 9× Postgres, Kafka,
│       │                                                  RabbitMQ, Redis, Keycloak
│       ├── AuthIntegrationTest.java                    ← login, token refresh, invalid credentials
│       ├── AccountOnboardingSagaTest.java              ← full orchestration saga + compensation path
│       ├── PaymentSagaChoreographyTest.java            ← full choreography saga + compensation path
│       ├── TransactionCqrsTest.java                    ← command → EventStore → Projection round-trip
│       ├── FraudDetectionGrpcTest.java                 ← gRPC fraud check end-to-end
│       ├── GraphqlQueryTest.java                       ← GraphQL queries through full stack
│       ├── KafkaEventFlowTest.java                     ← publish event → assert consumer processed it
│       └── ReportGenerationTest.java                   ← report request → S3 upload → presigned URL
│
│
├── ── OBSERVABILITY ────────────────────────────────────────────────────────────
│
└── observability/
    ├── prometheus/
    │   └── prometheus.yml                              ← scrape all /actuator/prometheus endpoints
    ├── grafana/
    │   ├── provisioning/
    │   │   ├── datasources/
    │   │   │   └── prometheus.yml
    │   │   └── dashboards/
    │   │       └── dashboards.yml
    │   └── dashboards/
    │       ├── finflow-overview.json                   ← TX throughput, error rate, latency p99
    │       ├── fraud-analytics.json                    ← fraud rate, blocked TXs, rule hits
    │       ├── saga-monitor.json                       ← saga success/failure/compensation rates
    │       └── jvm-metrics.json                        ← heap, GC, thread count per service
    ├── jaeger/
    │   └── jaeger.yml                                  ← sampling: 100% dev / 10% prod
    └── elk/
        ├── logstash/
        │   └── logstash.conf                           ← parses JSON logs → Elasticsearch
        ├── elasticsearch/
        │   └── elasticsearch.yml
        └── kibana/
            └── kibana.yml


═══════════════════════════════════════════════════════════════════════════════
 ✚ FILES ADDED (fixing Kafka + RabbitMQ ownership alignment)
═══════════════════════════════════════════════════════════════════════════════

  account-service/
    config/RabbitMQConfig.java              saga command/reply queue bindings
    config/KafkaProducerConfig.java         Kafka producer for audit-log topic
    saga/SagaReplyPublisher.java            publishes step result to saga.replies queue
    kafka/AccountEventPublisher.java        publishes audit-log events to Kafka

  payment-service/
    config/KafkaProducerConfig.java         Kafka producer for analytics/audit topics
    kafka/PaymentEventKafkaPublisher.java   publishes to analytics, audit-log, notify-events

  fraud-detection-service/
    config/KafkaProducerConfig.java         Kafka producer for fraud-events topic
    kafka/FraudEventPublisher.java          publishes fraud-events after scoring


═══════════════════════════════════════════════════════════════════════════════
 SERVICE PORT REFERENCE
═══════════════════════════════════════════════════════════════════════════════

  api-gateway                :8080
  graphql-gateway            :8081   → POST /graphql
  account-service            :8082
  transaction-service        :8083
  payment-service            :8084
  saga-orchestrator-service  :8085
  fraud-detection-service    :8086 HTTP  +  :9001 gRPC
  notification-service       :8087
  analytics-service          :8088
  report-service             :8089
  keycloak                   :9090
  rabbitmq                   :5672   management → :15672
  kafka (MSK)                :9092
  redis (ElastiCache)        :6379
  prometheus                 :9093
  grafana                    :3000
  jaeger UI                  :16686
  elasticsearch              :9200
  kibana                     :5601


═══════════════════════════════════════════════════════════════════════════════
 DATABASE OWNERSHIP  (strict database-per-service)
═══════════════════════════════════════════════════════════════════════════════

  account_db        → account-service
  transaction_db    → transaction-service   (event_store + projection tables)
  payment_db        → payment-service
  saga_db           → saga-orchestrator-service
  fraud_db          → fraud-detection-service
  notification_db   → notification-service
  analytics_db      → analytics-service
  report_db         → report-service
  keycloak_db       → keycloak (internal)


═══════════════════════════════════════════════════════════════════════════════
 KAFKA TOPIC OWNERSHIP
═══════════════════════════════════════════════════════════════════════════════

  transactions      producer: transaction-service
                    consumers: analytics-service, audit-consumer

  fraud-events      producer: fraud-detection-service
                    consumers: analytics-service, notification-service,
                               fraud-detection-service (async internal alerts)

  analytics         producer: transaction-service, payment-service
                    consumers: analytics-service

  audit-log         producer: transaction-service, payment-service, account-service
                    consumers: append-only (compliance/regulatory retention)

  notify-events     producer: transaction-service, payment-service
                    consumers: notification-service


═══════════════════════════════════════════════════════════════════════════════
 RABBITMQ EXCHANGE / QUEUE OWNERSHIP
═══════════════════════════════════════════════════════════════════════════════

  saga.commands  (direct exchange)
    publisher:  saga-orchestrator-service
    consumers:  account-service (CREATE_ACCOUNT, KYC_CHECK, CREATE_KEYCLOAK_USER)
                notification-service (SEND_WELCOME_EMAIL)

  saga.replies   (direct exchange)
    publisher:  account-service (SagaReplyPublisher)
    consumer:   saga-orchestrator-service (SagaReplyListener)

  payment.events (topic exchange)
    publisher:  transaction-service (PaymentInitiated)
                payment-service (PaymentCompleted / PaymentFailed)
    consumers:  payment-service (PaymentInitiated)
                transaction-service (PaymentCompleted / PaymentFailed)
                notification-service (PaymentCompleted / PaymentFailed)

  saga.dlx       (dead letter exchange)
    undeliverable/expired saga messages → manual inspection queue


═══════════════════════════════════════════════════════════════════════════════
 COMMUNICATION MAP
═══════════════════════════════════════════════════════════════════════════════

  REST / HTTP:
    Client          →  ALB → API Gateway        (all write operations)
    Client          →  ALB → GraphQL Gateway     (dashboard queries + subscriptions)
    API Gateway     →  Account Service           (POST/GET /accounts)
    API Gateway     →  Transaction Service       (POST/GET /transactions)
    API Gateway     →  Payment Service           (GET /payments)
    GraphQL Gateway →  Analytics Service         (GET /analytics/summary)
    GraphQL Gateway →  Report Service            (POST/GET /reports)
    GraphQL Gateway →  Account Service           (GET /accounts/{id})
    ALB             →  Keycloak                  (token validation via JWT filter)

  gRPC (sync):
    Transaction Service → Fraud Detection Service :9001  (CheckTransaction)
    [only this one gRPC call in the entire system]

  Kafka (async, event-driven):
    Transaction Service → Kafka → Analytics Service
    Transaction Service → Kafka → Notification Service
    Payment Service     → Kafka → Analytics Service
    Payment Service     → Kafka → Notification Service
    Fraud Detection     → Kafka → Analytics Service
    Fraud Detection     → Kafka → Notification Service
    Account Service     → Kafka → audit-log (append-only)
    Transaction Service → Kafka → audit-log (append-only)

  RabbitMQ (async, command/reply + saga):
    Saga Orchestrator   → RabbitMQ saga.commands → Account Service
    Saga Orchestrator   → RabbitMQ saga.commands → Notification Service
    Account Service     → RabbitMQ saga.replies  → Saga Orchestrator
    Transaction Service → RabbitMQ payment.events → Payment Service
    Payment Service     → RabbitMQ payment.events → Transaction Service
    Payment Service     → RabbitMQ payment.events → Notification Service


═══════════════════════════════════════════════════════════════════════════════
 13-DAY BUILD PLAN
═══════════════════════════════════════════════════════════════════════════════

  Day 01  Gradle setup, common module, proto module, Keycloak realm config
  Day 02  API Gateway: routes, JWT filter, Redis rate limiter, tracing filter
  Day 03  Account Service: CRUD, Keycloak client, Flyway, RabbitMQ+Kafka config
  Day 04  Transaction Service: CQRS write side, EventStore, aggregate, events
  Day 05  Transaction Service: CQRS read side, projections, Redis cache, gRPC client
  Day 06  Fraud Detection: gRPC server, rule engine, **Resilience4j**, Kafka publisher
  Day 07  Payment Service + RabbitMQ + Choreography Saga (happy + compensation)
  Day 08  Saga Orchestrator: state machine, steps, compensation engine
  Day 09  Analytics + Notification: Kafka consumers, Redis counters, templates
  Day 10  GraphQL Gateway: schema, resolvers, DataLoader, subscriptions
  Day 11  Report Service: PDF/CSV generation, S3 upload, presigned URLs
  Day 12  Observability: Jaeger, Prometheus, Grafana dashboards, ELK
  Day 13  Docker Compose → Kubernetes → AWS CDK → GitHub Actions CI/CD