package com.finflow.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import com.finflow.fraud.grpc.FraudCheckGrpcService;
import com.finflow.fraud.kafka.FraudEventPublisher;
import com.finflow.fraud.model.FraudRecord;
import com.finflow.fraud.repository.FraudRecordRepository;
import com.finflow.proto.fraud.FraudCheckProto;
import com.finflow.proto.fraud.FraudCheckServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("itest")
@Import(FraudIntegrationTest.TestSecurity.class)
@TestPropertySource(
        properties = {
            "spring.autoconfigure.exclude=net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration,org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
            "spring.kafka.listener.auto-startup=false"
        })
class FraudIntegrationTest {

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
                    .withDatabaseName("fraud_db")
                    .withUsername("finflow")
                    .withPassword("finflow");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void registerContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired private FraudCheckGrpcService fraudCheckGrpcService;
    @Autowired private FraudRecordRepository fraudRecordRepository;

    @MockBean private FraudEventPublisher fraudEventPublisher;

    private Server grpcServer;
    private ManagedChannel channel;
    private FraudCheckServiceGrpc.FraudCheckServiceBlockingStub stub;

    @BeforeEach
    void startInProcessGrpc() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        grpcServer =
                InProcessServerBuilder.forName(serverName)
                        .directExecutor()
                        .addService(fraudCheckGrpcService)
                        .build()
                        .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        stub = FraudCheckServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stopInProcessGrpc() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (grpcServer != null) {
            grpcServer.shutdownNow();
            grpcServer.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void fraudCheck_PersistsRecord_ForEveryCheck() {
        String txId = "int-tx-" + UUID.randomUUID();

        FraudCheckProto.FraudCheckRequest request =
                FraudCheckProto.FraudCheckRequest.newBuilder()
                        .setTransactionId(txId)
                        .setAccountId("acc-int-1")
                        .setAmount(500.0)
                        .setCurrency("USD")
                        .setCountryCode("US")
                        .build();

        FraudCheckProto.FraudCheckResponse response = stub.checkTransaction(request);

        assertThat(response.getFlagged()).isFalse();
        assertThat(response.getFraudScore()).isZero();

        FraudRecord saved =
                fraudRecordRepository
                        .findByTransactionId(txId)
                        .orElseThrow(() -> new AssertionError("expected persisted fraud record"));
        assertThat(saved.getAccountId()).isEqualTo("acc-int-1");
        assertThat(saved.getFraudScore()).isZero();
        assertThat(saved.getTransactionId()).isEqualTo(txId);
    }

    @Test
    void velocityRule_FlagsAccount_AfterMultipleTransactions() {
        String accountId = "vel-acc-" + UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            fraudRecordRepository.save(
                    FraudRecord.builder()
                            .transactionId("vel-pre-" + accountId + "-" + i)
                            .accountId(accountId)
                            .fraudScore(0)
                            .flagged(false)
                            .reason("clean")
                            .failureCode("CLEAN")
                            .triggeredRules("[]")
                            .amount(new BigDecimal("100"))
                            .currency("USD")
                            .countryCode("US")
                            .checkType("SYNC_GRPC")
                            .build());
        }

        String newTxId = "vel-new-" + UUID.randomUUID();
        FraudCheckProto.FraudCheckRequest request =
                FraudCheckProto.FraudCheckRequest.newBuilder()
                        .setTransactionId(newTxId)
                        .setAccountId(accountId)
                        .setAmount(100.0)
                        .setCurrency("USD")
                        .setCountryCode("US")
                        .build();

        FraudCheckProto.FraudCheckResponse response = stub.checkTransaction(request);

        assertThat(response.getFlagged()).isTrue();

        FraudRecord saved =
                fraudRecordRepository
                        .findByTransactionId(newTxId)
                        .orElseThrow(() -> new AssertionError("expected persisted fraud record"));
        assertThat(saved.getTriggeredRules()).contains("VELOCITY");
    }
}
