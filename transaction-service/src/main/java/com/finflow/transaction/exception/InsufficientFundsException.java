package com.finflow.transaction.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends FinFlowException {

    public InsufficientFundsException(String accountId, BigDecimal amount) {
        super(
                ErrorCode.INSUFFICIENT_FUNDS,
                "Insufficient funds in account: " + accountId + " for amount: " + amount,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
