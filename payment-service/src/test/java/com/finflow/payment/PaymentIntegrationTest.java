package com.finflow.payment;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.finflow.payment.config.RabbitMQConfig;
import com.finflow.payment.model.Payment;
import com.finflow.payment.model.PaymentStatus;
import com.finflow.payment.repository.LedgerRepository;
import com.finflow.payment.repository.PaymentRepository;
import com.finflow.payment.saga.PaymentInitiatedMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
@Import(PaymentIntegrationTest.TestSecurity.class)
@TestPropertySource(
        properties = {
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
            "spring.kafka.listener.auto-startup=false"
        })
class PaymentIntegrationTest {

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
                    .withDatabaseName("payment_db")
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

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private LedgerRepository ledgerRepository;

    @Test
    void fullPaymentFlow_CompletesSuccessfully() {
        String tx = "itx-" + UUID.randomUUID();
        PaymentInitiatedMessage msg =
                new PaymentInitiatedMessage(
                        tx,
                        "account-001",
                        BigDecimal.valueOf(100),
                        "USD",
                        "corr-" + tx,
                        Instant.now());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE,
                RabbitMQConfig.PAYMENT_INITIATED_ROUTING_KEY,
                msg);

        await().atMost(10, SECONDS)
                .until(() -> paymentRepository.findByTransactionId(tx).isPresent());

        Payment payment =
                paymentRepository
                        .findByTransactionId(tx)
                        .orElseThrow(() -> new AssertionError("missing payment"));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(ledgerRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getId()))
                .hasSize(2);
    }

    @Test
    void duplicatePayment_IsHandledIdempotently() {
        String tx = "dup-" + UUID.randomUUID();
        PaymentInitiatedMessage msg =
                new PaymentInitiatedMessage(
                        tx,
                        "account-001",
                        BigDecimal.valueOf(50),
                        "USD",
                        "corr-" + tx,
                        Instant.now());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE,
                RabbitMQConfig.PAYMENT_INITIATED_ROUTING_KEY,
                msg);
        await().atMost(10, SECONDS)
                .until(() -> paymentRepository.findByTransactionId(tx).isPresent());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE,
                RabbitMQConfig.PAYMENT_INITIATED_ROUTING_KEY,
                msg);

        await().atMost(10, SECONDS)
                .pollDelay(200, TimeUnit.MILLISECONDS)
                .untilAsserted(
                        () -> {
                            Optional<Payment> payment = paymentRepository.findByTransactionId(tx);
                            assertThat(payment).isPresent();
                            assertThat(
                                            paymentRepository.findAll().stream()
                                                    .filter(p -> tx.equals(p.getTransactionId()))
                                                    .count())
                                    .isEqualTo(1);
                            assertThat(
                                            ledgerRepository.findByPaymentIdOrderByCreatedAtAsc(
                                                    payment.get().getId()))
                                    .hasSize(2);
                        });
    }
}
