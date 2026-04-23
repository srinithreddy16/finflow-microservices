package com.finflow.payment.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import org.springframework.http.HttpStatus;

public class DuplicatePaymentException extends FinFlowException {

    public DuplicatePaymentException(String transactionId) {
        super(
                ErrorCode.LEDGER_ENTRY_FAILED,
                "Payment already exists for transaction: " + transactionId,
                HttpStatus.CONFLICT);
    }
}
