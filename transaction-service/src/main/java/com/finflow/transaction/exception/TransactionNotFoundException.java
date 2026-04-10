package com.finflow.transaction.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import org.springframework.http.HttpStatus;

public class TransactionNotFoundException extends FinFlowException {

    public TransactionNotFoundException(String transactionId) {
        super(
                ErrorCode.TRANSACTION_NOT_FOUND,
                "Transaction not found with id: " + transactionId,
                HttpStatus.NOT_FOUND);
    }
}
