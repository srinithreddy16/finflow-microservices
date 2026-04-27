package com.finflow.saga.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import org.springframework.http.HttpStatus;

public class SagaExecutionException extends FinFlowException {

    public SagaExecutionException(String sagaId, String message) {
        super(
                ErrorCode.SAGA_EXECUTION_FAILED,
                "Saga " + sagaId + " execution failed: " + message,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public SagaExecutionException(String sagaId, String message, Throwable cause) {
        super(
                ErrorCode.SAGA_EXECUTION_FAILED,
                "Saga " + sagaId + " execution failed: " + message,
                HttpStatus.INTERNAL_SERVER_ERROR,
                cause);
    }

    public static SagaExecutionException stepFailed(String sagaId, int step, String reason) {
        return new SagaExecutionException(sagaId, "step " + step + " failed: " + reason);
    }

    public static SagaExecutionException compensationFailed(String sagaId, String reason) {
        return new SagaExecutionException(sagaId, "compensation failed: " + reason);
    }
}
