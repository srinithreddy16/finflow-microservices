package com.finflow.payment.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends FinFlowException {

    public InsufficientBalanceException(
            String accountId, BigDecimal required, BigDecimal available) {
        super(
                ErrorCode.INSUFFICIENT_BALANCE,
                String.format(
                        "Insufficient balance in account %s. Required: %s, Available: %s",
                        accountId, required, available),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
