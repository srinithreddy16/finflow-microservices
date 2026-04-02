package com.finflow.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Standard JSON error body returned by FinFlow HTTP APIs.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final String errorCode;
    private final String message;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    private final String path;
    private final List<FieldError> details;

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return ErrorResponse.builder()
                .errorCode(errorCode.name())
                .message(message)
                .path(path)
                .build();
    }

    public static ErrorResponse withDetails(
            ErrorCode errorCode, String message, String path, List<FieldError> details) {
        return ErrorResponse.builder()
                .errorCode(errorCode.name())
                .message(message)
                .path(path)
                .details(details)
                .build();
    }

    @Getter
    @Builder
    public static class FieldError {
        private final String field;
        private final String message;
        private final Object rejectedValue;
    }
}
