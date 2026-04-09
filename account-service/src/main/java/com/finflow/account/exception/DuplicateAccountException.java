package com.finflow.account.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import org.springframework.http.HttpStatus;

public class DuplicateAccountException extends FinFlowException {

    public DuplicateAccountException(String email) {
        super(
                ErrorCode.ACCOUNT_ALREADY_EXISTS,
                "Account already exists with email: " + email,
                HttpStatus.CONFLICT);
    }
}
