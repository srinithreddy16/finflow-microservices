package com.finflow.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.fraud.grpc.FraudCheckGrpcService;
import com.finflow.fraud.model.FraudCheckRequest;
import com.finflow.fraud.model.FraudScore;
import com.finflow.fraud.service.FraudScoringService;
import com.finflow.proto.fraud.FraudCheckProto;
import com.finflow.proto.fraud.FraudCheckServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FraudCheckGrpcServiceTest {

    @Mock private FraudScoringService fraudScoringService;

    private FraudCheckGrpcService fraudCheckGrpcService;
    private Server grpcServer;
    private ManagedChannel channel;
    private FraudCheckServiceGrpc.FraudCheckServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        fraudCheckGrpcService = new FraudCheckGrpcService(fraudScoringService);
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
    void tearDown() throws Exception {
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
    void checkTransaction_ReturnsClean_WhenScoreIsLow() {
        FraudCheckProto.FraudCheckRequest request =
                FraudCheckProto.FraudCheckRequest.newBuilder()
                        .setTransactionId("tx-grpc-1")
                        .setAccountId("acc-001")
                        .setAmount(500.0)
                        .setCurrency("USD")
                        .setCountryCode("US")
                        .build();
        when(fraudScoringService.evaluateAndPersist(any(FraudCheckRequest.class), any()))
                .thenReturn(FraudScore.clean("tx-grpc-1", "acc-001"));

        FraudCheckProto.FraudCheckResponse response = stub.checkTransaction(request);

        assertThat(response.getFlagged()).isFalse();
        assertThat(response.getFraudScore()).isZero();
        verify(fraudScoringService).evaluateAndPersist(any(FraudCheckRequest.class), any());
    }

    @Test
    void checkTransaction_ReturnsFlagged_WhenScoreIsHigh() {
        FraudCheckProto.FraudCheckRequest request =
                FraudCheckProto.FraudCheckRequest.newBuilder()
                        .setTransactionId("tx-grpc-2")
                        .setAccountId("acc-001")
                        .setAmount(100.0)
                        .setCurrency("USD")
                        .setCountryCode("US")
                        .build();
        when(fraudScoringService.evaluateAndPersist(any(FraudCheckRequest.class), any()))
                .thenReturn(
                        FraudScore.flagged(
                                "tx-grpc-2",
                                "acc-001",
                                85,
                                "fraud",
                                "VELOCITY",
                                List.of("VELOCITY")));

        FraudCheckProto.FraudCheckResponse response = stub.checkTransaction(request);

        assertThat(response.getFlagged()).isTrue();
        assertThat(response.getFraudScore()).isEqualTo(85);
    }

    @Test
    void checkTransaction_ReturnsInvalidArgument_WhenTransactionIdBlank() {
        FraudCheckProto.FraudCheckRequest request =
                FraudCheckProto.FraudCheckRequest.newBuilder()
                        .setTransactionId("")
                        .setAccountId("acc-001")
                        .setAmount(100.0)
                        .setCurrency("USD")
                        .setCountryCode("US")
                        .build();

        assertThatThrownBy(() -> stub.checkTransaction(request))
                .isInstanceOf(StatusRuntimeException.class)
                .extracting(ex -> ((StatusRuntimeException) ex).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
}
