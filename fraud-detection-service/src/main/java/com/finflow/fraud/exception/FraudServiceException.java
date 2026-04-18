package com.finflow.fraud.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import org.springframework.http.HttpStatus;

public class FraudServiceException extends FinFlowException {

    public FraudServiceException(String message) {
        super(ErrorCode.INTERNAL_SERVER_ERROR, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public FraudServiceException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL_SERVER_ERROR, message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public static FraudServiceException ruleEngineError(String transactionId, Throwable cause) {
        return new FraudServiceException(
                "Rule engine error for transaction: " + transactionId, cause);
    }
}
