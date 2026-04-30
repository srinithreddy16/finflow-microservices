package com.finflow.graphql.resolver;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.graphql.cache.GraphqlRedisCache;
import com.finflow.graphql.client.ReportServiceClient;
import com.finflow.graphql.client.dto.GenerateReportInputDto;
import com.finflow.graphql.client.dto.ReportDto;
import com.finflow.graphql.client.dto.ReportPageDto;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
@RequiredArgsConstructor
public class ReportQueryResolver {

    private final ReportServiceClient reportServiceClient;
    private final GraphqlRedisCache graphqlRedisCache;

    @QueryMapping
    public Object report(@Argument String id) {
        Object cached = graphqlRedisCache.getReport(id).orElse(null);
        if (cached != null) {
            return cached;
        }

        try {
            ReportDto report = reportServiceClient.getReport(id);
            graphqlRedisCache.cacheReport(id, report);
            log.debug("Report fetched: {}, status: {}", report.id(), report.status());
            return report;
        } catch (FinFlowException ex) {
            if (ex.getErrorCode() == ErrorCode.REPORT_NOT_FOUND) {
                return null;
            }
            throw ex;
        }
    }

    @QueryMapping
    public Object reportsByAccount(
            @Argument String accountId,
            @Argument String status,
            @Argument Integer page,
            @Argument Integer size) {
        int resolvedPage = page != null ? page : 0;
        int resolvedSize = size != null ? size : 10;
        ReportPageDto result =
                reportServiceClient.getReportsByAccount(accountId, status, resolvedPage, resolvedSize);
        log.debug("Reports fetched for account: {}, status: {}", accountId, status);
        return result;
    }

    @MutationMapping
    public Object generateReport(
            @Argument GenerateReportInputDto input, @AuthenticationPrincipal Jwt jwt) {
        try {
            log.info(
                    "Report generation requested: type={}, account={}",
                    input.reportType(),
                    input.accountId());
            ReportDto generated = reportServiceClient.generateReport(input);
            if (generated != null && generated.id() != null) {
                graphqlRedisCache.evict("graphql:report:" + generated.id());
            }
            return generated;
        } catch (FinFlowException ex) {
            throw ex;
        } catch (Exception ex) {
            throw FinFlowException.internalError(
                    ErrorCode.REPORT_GENERATION_FAILED, "Failed to generate report", ex);
        }
    }

    @SchemaMapping(typeName = "Report", field = "expiresAt")
    public Instant resolveExpiresAt(ReportDto report) {
        if (report.generatedAt() == null) {
            return null;
        }
        return report.generatedAt().plus(Duration.ofHours(1));
    }
}
