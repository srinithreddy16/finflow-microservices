package com.finflow.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.proto.fraud.FraudCheckProto.FraudCheckResponse;
import com.finflow.transaction.config.ItestCacheConfig;
import com.finflow.transaction.grpc.FraudCheckGrpcClient;
import com.finflow.transaction.kafka.TransactionEventPublisher;
import com.finflow.transaction.saga.PaymentSagaInitiator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("itest")
@Import({ItestCacheConfig.class, TransactionIntegrationTest.TestSecurity.class})
@TestPropertySource(
        properties = {
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
        })
class TransactionIntegrationTest {

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurity {
        @Bean
        SecurityFilterChain integrationSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("transaction_db")
                    .withUsername("finflow")
                    .withPassword("finflow");

    @Container
    static final RabbitMQContainer rabbit =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void registerContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired private TestRestTemplate restTemplate;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private FraudCheckGrpcClient fraudCheckGrpcClient;

    @MockBean private PaymentSagaInitiator paymentSagaInitiator;

    @MockBean private TransactionEventPublisher transactionEventPublisher;

    @BeforeEach
    void stubFraud() {
        when(fraudCheckGrpcClient.checkTransaction(
                        anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(
                        FraudCheckResponse.newBuilder().setFlagged(false).setFraudScore(0).build());
    }

    @Test
    void createAndRetrieveTransaction_FullFlow() throws Exception {
        String json =
                """
                {"accountId":"acc-int-001","amount":500.00,"currency":"USD"}
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> created =
                restTemplate.postForEntity(
                        "/api/transactions", new HttpEntity<>(json, headers), String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = objectMapper.readTree(created.getBody());
        String transactionId = body.get("id").asText();
        assertThat(body.get("status").asText()).isEqualTo("PENDING");

        ResponseEntity<String> fetched =
                restTemplate.getForEntity("/api/transactions/" + transactionId, String.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(fetched.getBody()).get("status").asText())
                .isEqualTo("PENDING");

        Long eventRows =
                jdbcTemplate.queryForObject(
                        "select count(*) from event_store where aggregate_id = ?",
                        Long.class,
                        transactionId);
        assertThat(eventRows).isEqualTo(1L);

        Long projectionRows =
                jdbcTemplate.queryForObject(
                        "select count(*) from transaction_projection where id = ?",
                        Long.class,
                        transactionId);
        assertThat(projectionRows).isEqualTo(1L);
    }

    @Test
    void eventStore_IsAppendOnly() throws Exception {
        String json =
                """
                {"accountId":"acc-int-002","amount":100.00,"currency":"USD"}
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> created =
                restTemplate.postForEntity(
                        "/api/transactions", new HttpEntity<>(json, headers), String.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String transactionId = objectMapper.readTree(created.getBody()).get("id").asText();

        Long oneEvent =
                jdbcTemplate.queryForObject(
                        "select count(*) from event_store where aggregate_id = ?",
                        Long.class,
                        transactionId);
        assertThat(oneEvent).isEqualTo(1L);

        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String completeJson =
                """
                {"paymentId":"pay-001","correlationId":"corr-complete"}
                """;
        ResponseEntity<Void> patched =
                restTemplate.exchange(
                        "/api/transactions/" + transactionId + "/complete",
                        HttpMethod.PATCH,
                        new HttpEntity<>(completeJson, headers),
                        Void.class);

        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

        Long twoEvents =
                jdbcTemplate.queryForObject(
                        "select count(*) from event_store where aggregate_id = ?",
                        Long.class,
                        transactionId);
        assertThat(twoEvents).isEqualTo(2L);

        var sequences =
                jdbcTemplate.query(
                        "select sequence_number from event_store where aggregate_id = ? order by sequence_number",
                        (rs, row) -> rs.getLong("sequence_number"),
                        transactionId);
        assertThat(sequences).containsExactly(1L, 2L);

        String status =
                jdbcTemplate.queryForObject(
                        "select status from transaction_projection where id = ?",
                        String.class,
                        transactionId);
        assertThat(status).isEqualTo("COMPLETED");
    }
}
