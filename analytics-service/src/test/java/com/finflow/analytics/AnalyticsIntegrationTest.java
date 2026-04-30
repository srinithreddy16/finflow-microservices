package com.finflow.analytics;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.finflow.analytics.cache.AnalyticsRedisCache;
import com.finflow.analytics.consumer.AnalyticsEventConsumer.TransactionKafkaEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
            "spring.cache.type=simple",
            "spring.data.redis.host=invalid-host-to-skip",
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
            "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
            "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
        })
class AnalyticsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("analytics_db")
                    .withUsername("finflow")
                    .withPassword("finflow");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @MockBean private AnalyticsRedisCache analyticsRedisCache;

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM analytics_read_model");
    }

    @Test
    void consumeAndAggregate_PersistsModel_AfterKafkaEvent() {
        String accountId = "acc-" + UUID.randomUUID();
        TransactionKafkaEvent event =
                new TransactionKafkaEvent(
                        "evt-1",
                        "tx-1",
                        accountId,
                        BigDecimal.valueOf(500),
                        "USD",
                        "TRANSACTION_COMPLETED",
                        "COMPLETED",
                        "corr-1",
                        Instant.now());

        kafkaTemplate.send("transactions", accountId, event);

        await().atMost(30, SECONDS)
                .pollInterval(1, SECONDS)
                .until(
                        () ->
                                jdbcTemplate.queryForObject(
                                                "SELECT COUNT(*) FROM analytics_read_model WHERE account_id = ?",
                                                Integer.class,
                                                accountId)
                                        == 1);

        Integer totalTransactions =
                jdbcTemplate.queryForObject(
                        "SELECT total_transactions FROM analytics_read_model WHERE account_id = ?",
                        Integer.class,
                        accountId);
        assertThat(totalTransactions).isEqualTo(1);
    }

    @Test
    void consumeMultipleEvents_AggregatesCorrectly() {
        String accountId = "acc-" + UUID.randomUUID();

        kafkaTemplate.send(
                "transactions",
                accountId,
                new TransactionKafkaEvent(
                        "evt-1",
                        "tx-1",
                        accountId,
                        BigDecimal.valueOf(100),
                        "USD",
                        "TRANSACTION_COMPLETED",
                        "COMPLETED",
                        "corr-1",
                        Instant.now()));
        kafkaTemplate.send(
                "transactions",
                accountId,
                new TransactionKafkaEvent(
                        "evt-2",
                        "tx-2",
                        accountId,
                        BigDecimal.valueOf(200),
                        "USD",
                        "TRANSACTION_COMPLETED",
                        "COMPLETED",
                        "corr-2",
                        Instant.now()));
        kafkaTemplate.send(
                "transactions",
                accountId,
                new TransactionKafkaEvent(
                        "evt-3",
                        "tx-3",
                        accountId,
                        BigDecimal.valueOf(300),
                        "USD",
                        "TRANSACTION_COMPLETED",
                        "COMPLETED",
                        "corr-3",
                        Instant.now()));

        await().atMost(30, SECONDS)
                .pollInterval(1, SECONDS)
                .until(
                        () ->
                                jdbcTemplate.queryForObject(
                                                "SELECT completed_transactions FROM analytics_read_model WHERE account_id = ?",
                                                Integer.class,
                                                accountId)
                                        == 3);

        BigDecimal totalVolume =
                jdbcTemplate.queryForObject(
                        "SELECT total_volume FROM analytics_read_model WHERE account_id = ?",
                        BigDecimal.class,
                        accountId);
        assertThat(totalVolume).isEqualByComparingTo("600.0000");
    }
}
