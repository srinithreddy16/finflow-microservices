package com.finflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base runtime exception for FinFlow, pairing a domain {@link ErrorCode} with an HTTP status.
 */
public class FinFlowException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public FinFlowException(ErrorCode errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public FinFlowException(ErrorCode errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public static FinFlowException notFound(ErrorCode errorCode, String message) {
        return new FinFlowException(errorCode, message, HttpStatus.NOT_FOUND);
    }

    public static FinFlowException badRequest(ErrorCode errorCode, String message) {
        return new FinFlowException(errorCode, message, HttpStatus.BAD_REQUEST);
    }

    public static FinFlowException unauthorized(String message) {
        return new FinFlowException(ErrorCode.UNAUTHORIZED, message, HttpStatus.UNAUTHORIZED);
    }

    public static FinFlowException forbidden(String message) {
        return new FinFlowException(ErrorCode.FORBIDDEN, message, HttpStatus.FORBIDDEN);
    }

    public static FinFlowException internalError(ErrorCode errorCode, String message, Throwable cause) {
        return new FinFlowException(errorCode, message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
