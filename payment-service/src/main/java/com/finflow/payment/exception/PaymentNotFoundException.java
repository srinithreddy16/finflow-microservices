package com.finflow.payment.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import org.springframework.http.HttpStatus;

public class PaymentNotFoundException extends FinFlowException {

    public PaymentNotFoundException(String paymentId) {
        super(
                ErrorCode.PAYMENT_NOT_FOUND,
                "Payment not found with id: " + paymentId,
                HttpStatus.NOT_FOUND);
    }

    public PaymentNotFoundException(String field, String value) {
        super(
                ErrorCode.PAYMENT_NOT_FOUND,
                "Payment not found with " + field + ": " + value,
                HttpStatus.NOT_FOUND);
    }
}
