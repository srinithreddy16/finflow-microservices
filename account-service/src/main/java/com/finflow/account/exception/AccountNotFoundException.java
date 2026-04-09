package com.finflow.account.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends FinFlowException {

    public AccountNotFoundException(String accountId) {
        super(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found with id: " + accountId,
                HttpStatus.NOT_FOUND);
    }

    public AccountNotFoundException(String field, String value) {
        super(
                ErrorCode.ACCOUNT_NOT_FOUND,
                "Account not found with " + field + ": " + value,
                HttpStatus.NOT_FOUND);
    }
}
