package com.finflow.transaction.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.ErrorResponse;
import com.finflow.common.exception.FinFlowException;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FinFlowException.class)
    public ResponseEntity<ErrorResponse> handleFinFlowException(
            FinFlowException ex, HttpServletRequest request) {
        log.warn(
                "FinFlow exception: {} - {}",
                ex.getErrorCode(),
                ex.getMessage());
        ErrorResponse body =
                ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> details =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                fe ->
                                        ErrorResponse.FieldError.builder()
                                                .field(fe.getField())
                                                .message(fe.getDefaultMessage())
                                                .rejectedValue(fe.getRejectedValue())
                                                .build())
                        .toList();

        ErrorResponse body =
                ErrorResponse.withDetails(
                        ErrorCode.VALIDATION_FAILED,
                        "Validation failed",
                        request.getRequestURI(),
                        details);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        log.warn("Illegal state: {}", ex.getMessage());
        ErrorResponse body =
                ErrorResponse.of(
                        ErrorCode.VALIDATION_FAILED, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ErrorResponse body =
                ErrorResponse.of(
                        ErrorCode.VALIDATION_FAILED, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ErrorResponse> handleStatusRuntime(
            StatusRuntimeException ex, HttpServletRequest request) {
        log.warn("gRPC error: {}", ex.getStatus(), ex);
        String message =
                ex.getStatus() != null ? ex.getStatus().toString() : "gRPC call failed";
        ErrorResponse body =
                ErrorResponse.of(
                        ErrorCode.SERVICE_UNAVAILABLE, message, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        ErrorResponse body =
                ErrorResponse.of(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred",
                        request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
