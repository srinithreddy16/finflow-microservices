package com.finflow.graphql.config;

import com.finflow.common.exception.FinFlowException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * GraphQL runtime configuration including error handling, schema customization, and
 * instrumentation.
 *
 * <p>This is the global error handler for all GraphQL resolvers and prevents raw exceptions from
 * leaking to clients.
 */
@Configuration
@Slf4j
public class GraphqlConfig {

    @Bean
    public DataFetcherExceptionResolverAdapter graphqlExceptionResolver() {
        return new DataFetcherExceptionResolverAdapter() {
            @Override
            protected GraphQLError resolveToSingleError(
                    Throwable ex, DataFetchingEnvironment env) {
                if (ex instanceof FinFlowException finFlowException) {
                    HttpStatus status = finFlowException.getHttpStatus();
                    ErrorType errorType =
                            status != null && status.is4xxClientError()
                                    ? ErrorType.BAD_REQUEST
                                    : ErrorType.INTERNAL_ERROR;

                    if (status != null && status.is4xxClientError()) {
                        log.warn(
                                "GraphQL FinFlow 4xx error: code={}, message={}",
                                finFlowException.getErrorCode(),
                                finFlowException.getMessage());
                    } else {
                        log.error(
                                "GraphQL FinFlow 5xx error: code={}, message={}",
                                finFlowException.getErrorCode(),
                                finFlowException.getMessage(),
                                finFlowException);
                    }

                    return GraphqlErrorBuilder.newError(env)
                            .message(finFlowException.getMessage())
                            .errorType(errorType)
                            .extensions(
                                    Map.of(
                                            "errorCode",
                                            finFlowException.getErrorCode() != null
                                                    ? finFlowException.getErrorCode().name()
                                                    : "INTERNAL_SERVER_ERROR"))
                            .build();
                }

                if (ex instanceof WebClientResponseException webEx) {
                    HttpStatus status = HttpStatus.resolve(webEx.getStatusCode().value());
                    ErrorType errorType;
                    if (webEx.getStatusCode().value() == 404) {
                        errorType = ErrorType.NOT_FOUND;
                        log.warn("GraphQL downstream 404: {}", webEx.getMessage());
                    } else if (webEx.getStatusCode().value() == 403) {
                        errorType = ErrorType.FORBIDDEN;
                        log.warn("GraphQL downstream 403: {}", webEx.getMessage());
                    } else {
                        errorType = ErrorType.INTERNAL_ERROR;
                        if (status != null && status.is4xxClientError()) {
                            log.warn("GraphQL downstream 4xx: {}", webEx.getMessage());
                        } else {
                            log.error("GraphQL downstream 5xx/client error", webEx);
                        }
                    }

                    return GraphqlErrorBuilder.newError(env)
                            .message(webEx.getStatusText() != null ? webEx.getStatusText() : "Downstream service error")
                            .errorType(errorType)
                            .build();
                }

                log.error("GraphQL unhandled exception", ex);
                return GraphqlErrorBuilder.newError(env)
                        .message("Internal server error")
                        .errorType(ErrorType.INTERNAL_ERROR)
                        .build();
            }
        };
    }
}
