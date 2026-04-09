package com.finflow.account.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import org.springframework.http.HttpStatus;

public class TenantNotFoundException extends FinFlowException {

    public TenantNotFoundException(String tenantId) {
        super(
                ErrorCode.TENANT_NOT_FOUND,
                "Tenant not found with id: " + tenantId,
                HttpStatus.NOT_FOUND);
    }
}
