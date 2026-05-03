package com.finflow.fraud.grpc;

import com.finflow.common.exception.FinFlowException;
import com.finflow.fraud.model.FraudCheckRequest;
import com.finflow.fraud.model.FraudScore;
import com.finflow.fraud.service.FraudScoringService;
import com.finflow.proto.fraud.FraudCheckProto.FraudCheckResponse;
import com.finflow.proto.fraud.FraudCheckServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC server implementation for fraud checking.
 *
 * <p>This runs synchronously in the payment flow: transaction-service → (gRPC) →
 * FraudCheckGrpcService → rule engine
 *
 * <p>The gRPC server is started by net.devh grpc-server-spring-boot-starter on port 9001
 * (configured in application.yml under grpc.server.port).
 *
 * <p>Response time target: &lt; 50ms p99 The Resilience4j circuit breaker in transaction-service
 * will open after 50% failure rate and use a fallback (allow transaction) to prevent fraud service
 * outage from blocking all payments.
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class FraudCheckGrpcService extends FraudCheckServiceGrpc.FraudCheckServiceImplBase {

    private static final String CHECK_TYPE_SYNC_GRPC = "SYNC_GRPC";

    private final FraudScoringService fraudScoringService;
    private final MeterRegistry meterRegistry;

    @Override
    public void checkTransaction(
            com.finflow.proto.fraud.FraudCheckProto.FraudCheckRequest request,
            StreamObserver<FraudCheckResponse> responseObserver) {
        log.info(
                "gRPC fraud check received for transaction: {}, account: {}, amount: {} {}",
                request.getTransactionId(),
                request.getAccountId(),
                request.getAmount(),
                request.getCurrency());

        try {
            if (request.getTransactionId() == null || request.getTransactionId().isBlank()) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("transactionId is required")
                                .asRuntimeException());
                return;
            }
            if (request.getAccountId() == null || request.getAccountId().isBlank()) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("accountId is required")
                                .asRuntimeException());
                return;
            }
            if (request.getAmount() <= 0) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("amount must be positive")
                                .asRuntimeException());
                return;
            }

            FraudCheckRequest domainRequest =
                    new FraudCheckRequest(
                            request.getTransactionId(),
                            request.getAccountId(),
                            BigDecimal.valueOf(request.getAmount()),
                            request.getCurrency(),
                            request.getCountryCode());

            FraudScore fraudScore =
                    fraudScoringService.evaluateAndPersist(domainRequest, CHECK_TYPE_SYNC_GRPC);

            meterRegistry
                    .counter("fraud.checks.total", "service", "fraud-detection-service")
                    .increment();
            if (fraudScore.flagged()) {
                meterRegistry
                        .counter("fraud.checks.flagged", "service", "fraud-detection-service")
                        .increment();
            }
            meterRegistry
                    .summary("fraud.score.distribution", "service", "fraud-detection-service")
                    .record(fraudScore.score());

            FraudCheckResponse response =
                    FraudCheckResponse.newBuilder()
                            .setTransactionId(request.getTransactionId())
                            .setFraudScore(fraudScore.score())
                            .setFlagged(fraudScore.flagged())
                            .setReason(fraudScore.reason())
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info(
                    "gRPC fraud check completed: transaction={}, flagged={}, score={}",
                    request.getTransactionId(),
                    fraudScore.flagged(),
                    fraudScore.score());
        } catch (FinFlowException e) {
            log.error("FinFlow error during gRPC fraud check: {}", e.getMessage());
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Internal fraud service error", e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Internal fraud service error")
                            .asRuntimeException());
        }
    }
}
