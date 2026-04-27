package com.finflow.saga;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.finflow.saga.config.RabbitMQConfig;
import com.finflow.saga.steps.SagaReply;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import(SagaIntegrationTest.TestSecurity.class)
@TestPropertySource(
        properties = {
            "spring.main.allow-bean-definition-overriding=true",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
        })
class SagaIntegrationTest {

    @TestConfiguration
    @EnableWebSecurity
    static class TestSecurity {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("saga_db")
                    .withUsername("finflow")
                    .withPassword("finflow");

    @Container
    static final RabbitMQContainer rabbitmq =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitAdmin rabbitAdmin;

    private String commandTapQueue;
    private String compensationTapQueue;

    @BeforeEach
    void setUpQueues() {
        Queue commandQueue = new AnonymousQueue();
        rabbitAdmin.declareQueue(commandQueue);
        commandTapQueue = commandQueue.getName();

        Binding commandBinding =
                BindingBuilder.bind(commandQueue)
                        .to(new DirectExchange(RabbitMQConfig.SAGA_COMMANDS_EXCHANGE))
                        .with(RabbitMQConfig.ACCOUNT_COMMANDS_ROUTING_KEY);
        rabbitAdmin.declareBinding(commandBinding);

        Queue compensationQueue = new AnonymousQueue();
        rabbitAdmin.declareQueue(compensationQueue);
        compensationTapQueue = compensationQueue.getName();

        Binding compensationBinding =
                BindingBuilder.bind(compensationQueue)
                        .to(new DirectExchange(RabbitMQConfig.SAGA_COMMANDS_EXCHANGE))
                        .with("saga.account.compensation");
        rabbitAdmin.declareBinding(compensationBinding);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM saga_instances");
        if (commandTapQueue != null) {
            rabbitAdmin.deleteQueue(commandTapQueue);
        }
        if (compensationTapQueue != null) {
            rabbitAdmin.deleteQueue(compensationTapQueue);
        }
    }

    @Test
    void startSaga_PersistsInstance_AndPublishesStep1Command() {
        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        "http://localhost:" + port + "/api/saga/onboarding",
                        Map.of(
                                "email", "a@finflow.com",
                                "firstName", "A",
                                "lastName", "User",
                                "phoneNumber", "555-0101",
                                "tenantId", "tenant-1"),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM saga_instances", Integer.class);
        assertThat(count).isEqualTo(1);

        String state =
                jdbcTemplate.queryForObject(
                        "SELECT state FROM saga_instances LIMIT 1", String.class);
        assertThat(state).isEqualTo("STEP_1_PENDING");

        Object message = rabbitTemplate.receiveAndConvert(commandTapQueue, 5000);
        assertThat(message).isNotNull();
    }

    @Test
    void fullHappyPath_CompletesSuccessfully() {
        String corr = "corr-" + UUID.randomUUID();
        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        "http://localhost:" + port + "/api/saga/onboarding",
                        Map.of(
                                "email", "happy@finflow.com",
                                "firstName", "Happy",
                                "lastName", "Path",
                                "phoneNumber", "555-0102",
                                "tenantId", "tenant-1",
                                "correlationId", corr),
                        Map.class);
        String sagaId = (String) response.getBody().get("sagaId");

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SAGA_REPLIES_EXCHANGE,
                RabbitMQConfig.SAGA_REPLIES_ROUTING_KEY,
                new SagaReply(sagaId, "CREATE_ACCOUNT", true, "acc-001", null, corr));
        await().atMost(10, SECONDS)
                .until(
                        () ->
                                "STEP_2_PENDING".equals(
                                        jdbcTemplate.queryForObject(
                                                "SELECT state FROM saga_instances WHERE saga_id = ?",
                                                String.class,
                                                sagaId)));

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SAGA_REPLIES_EXCHANGE,
                RabbitMQConfig.SAGA_REPLIES_ROUTING_KEY,
                new SagaReply(sagaId, "VERIFY_KYC", true, "kyc-ok", null, corr));
        await().atMost(10, SECONDS)
                .until(
                        () ->
                                "STEP_3_PENDING".equals(
                                        jdbcTemplate.queryForObject(
                                                "SELECT state FROM saga_instances WHERE saga_id = ?",
                                                String.class,
                                                sagaId)));

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SAGA_REPLIES_EXCHANGE,
                RabbitMQConfig.SAGA_REPLIES_ROUTING_KEY,
                new SagaReply(sagaId, "CREATE_KEYCLOAK_USER", true, "kc-001", null, corr));
        await().atMost(10, SECONDS)
                .until(
                        () ->
                                "STEP_4_PENDING".equals(
                                        jdbcTemplate.queryForObject(
                                                "SELECT state FROM saga_instances WHERE saga_id = ?",
                                                String.class,
                                                sagaId)));

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SAGA_REPLIES_EXCHANGE,
                RabbitMQConfig.SAGA_REPLIES_ROUTING_KEY,
                new SagaReply(sagaId, "SEND_WELCOME_EMAIL", true, "sent", null, corr));
        await().atMost(10, SECONDS)
                .until(
                        () ->
                                "COMPLETED".equals(
                                        jdbcTemplate.queryForObject(
                                                "SELECT state FROM saga_instances WHERE saga_id = ?",
                                                String.class,
                                                sagaId)));

        java.sql.Timestamp completedAt =
                jdbcTemplate.queryForObject(
                        "SELECT completed_at FROM saga_instances WHERE saga_id = ?",
                        java.sql.Timestamp.class,
                        sagaId);
        assertThat(completedAt).isNotNull();
    }

    @Test
    void compensationPath_FailsAtStep2_RollsBackStep1() {
        String corr = "corr-" + UUID.randomUUID();
        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        "http://localhost:" + port + "/api/saga/onboarding",
                        Map.of(
                                "email", "comp@finflow.com",
                                "firstName", "Comp",
                                "lastName", "Path",
                                "phoneNumber", "555-0103",
                                "tenantId", "tenant-1",
                                "correlationId", corr),
                        Map.class);
        String sagaId = (String) response.getBody().get("sagaId");

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SAGA_REPLIES_EXCHANGE,
                RabbitMQConfig.SAGA_REPLIES_ROUTING_KEY,
                new SagaReply(sagaId, "CREATE_ACCOUNT", true, "acc-rollback", null, corr));
        await().atMost(10, SECONDS)
                .until(
                        () ->
                                "STEP_2_PENDING".equals(
                                        jdbcTemplate.queryForObject(
                                                "SELECT state FROM saga_instances WHERE saga_id = ?",
                                                String.class,
                                                sagaId)));

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SAGA_REPLIES_EXCHANGE,
                RabbitMQConfig.SAGA_REPLIES_ROUTING_KEY,
                new SagaReply(sagaId, "VERIFY_KYC", false, null, "KYC check failed", corr));

        await().atMost(10, SECONDS)
                .until(
                        () ->
                                "FAILED".equals(
                                        jdbcTemplate.queryForObject(
                                                "SELECT state FROM saga_instances WHERE saga_id = ?",
                                                String.class,
                                                sagaId)));

        Integer failureStep =
                jdbcTemplate.queryForObject(
                        "SELECT failure_step FROM saga_instances WHERE saga_id = ?",
                        Integer.class,
                        sagaId);
        assertThat(failureStep).isEqualTo(2);

        Object compensationMessage = rabbitTemplate.receiveAndConvert(compensationTapQueue, 5000);
        assertThat(compensationMessage).isNotNull();
    }
}
