package com.finflow.transaction.grpc;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.proto.fraud.FraudCheckProto.FraudCheckRequest;
import com.finflow.proto.fraud.FraudCheckProto.FraudCheckResponse;
import com.finflow.proto.fraud.FraudCheckServiceGrpc;
import io.grpc.StatusRuntimeException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * gRPC client that calls fraud-detection-service synchronously before committing a transaction.
 * Wrapped with Resilience4j circuit breaker and retry.
 */
@Service
@Slf4j
public class FraudCheckGrpcClient {

    @GrpcClient("fraud-detection-service")
    private FraudCheckServiceGrpc.FraudCheckServiceBlockingStub blockingStub;

    private FraudCheckGrpcClient self;

    @Autowired
    public void setSelf(@Lazy FraudCheckGrpcClient self) {
        this.self = self;
    }

    public FraudCheckResponse checkTransaction(
            String transactionId,
            String accountId,
            BigDecimal amount,
            String currency,
            String countryCode) {
        FraudCheckRequest request =
                FraudCheckRequest.newBuilder()
                        .setTransactionId(transactionId)
                        .setAccountId(accountId)
                        .setAmount(amount.doubleValue())
                        .setCurrency(currency)
                        .setCountryCode(countryCode)
                        .build();

        try {
            FraudCheckResponse response = self.checkTransactionInternal(request);
            log.info(
                    "Fraud check for transaction {}: score={}, flagged={}",
                    transactionId,
                    response.getFraudScore(),
                    response.getFlagged());
            return response;
        } catch (StatusRuntimeException e) {
            log.error(
                    "gRPC fraud check failed for transaction {}: {}",
                    transactionId,
                    e.getStatus(),
                    e);
            throw FinFlowException.internalError(
                    ErrorCode.FRAUD_CHECK_FAILED,
                    "Fraud check call failed: " + e.getStatus(),
                    e);
        }
    }

    @CircuitBreaker(name = "fraud-service", fallbackMethod = "fraudCheckFallback")
    @Retry(name = "fraud-service")
    public FraudCheckResponse checkTransactionInternal(FraudCheckRequest request) {
        return blockingStub.checkTransaction(request);
    }

    @SuppressWarnings("unused")
    private FraudCheckResponse fraudCheckFallback(FraudCheckRequest request, Exception ex) {
        log.warn(
                "Fraud service unavailable, using fallback for transaction: {}",
                request.getTransactionId());
        return FraudCheckResponse.newBuilder()
                .setTransactionId(request.getTransactionId())
                .setFraudScore(0)
                .setFlagged(false)
                .setReason("Fraud service circuit breaker open — transaction allowed")
                .build();
    }
}
