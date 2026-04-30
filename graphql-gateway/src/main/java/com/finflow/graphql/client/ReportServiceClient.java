package com.finflow.graphql.client;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.graphql.client.dto.GenerateReportInputDto;
import com.finflow.graphql.client.dto.ReportDto;
import com.finflow.graphql.client.dto.ReportPageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ReportServiceClient {

    private final WebClient webClient;

    public ReportServiceClient(
            @Value("${service.report.url:http://localhost:8089}") String reportUrl,
            WebClient.Builder webClientBuilder) {
        this.webClient = WebClient.create(reportUrl);
    }

    public ReportDto getReport(String reportId) {
        try {
            return webClient
                    .get()
                    .uri("/api/reports/{reportId}", reportId)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response ->
                                    Mono.error(
                                            FinFlowException.notFound(
                                                    ErrorCode.REPORT_NOT_FOUND,
                                                    "Report not found: " + reportId)))
                    .bodyToMono(ReportDto.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Failed fetching report: {}", reportId, ex);
            throw FinFlowException.internalError(
                    ErrorCode.SERVICE_UNAVAILABLE, "Failed to fetch report", ex);
        }
    }

    public ReportPageDto getReportsByAccount(String accountId, String status, int page, int size) {
        try {
            return webClient
                    .get()
                    .uri(
                            uriBuilder ->
                                    uriBuilder.path("/api/reports/account/{accountId}")
                                            .queryParam("status", status)
                                            .queryParam("page", page)
                                            .queryParam("size", size)
                                            .build(accountId))
                    .retrieve()
                    .onStatus(
                            s -> s.is4xxClientError(),
                            response ->
                                    Mono.error(
                                            FinFlowException.notFound(
                                                    ErrorCode.REPORT_NOT_FOUND,
                                                    "Reports not found for account: " + accountId)))
                    .bodyToMono(ReportPageDto.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Failed fetching reports for account: {}", accountId, ex);
            throw FinFlowException.internalError(
                    ErrorCode.SERVICE_UNAVAILABLE, "Failed to fetch reports", ex);
        }
    }

    public ReportDto generateReport(GenerateReportInputDto input) {
        try {
            return webClient
                    .post()
                    .uri("/api/reports")
                    .bodyValue(input)
                    .retrieve()
                    .onStatus(
                            s -> s.is4xxClientError(),
                            response ->
                                    Mono.error(
                                            FinFlowException.badRequest(
                                                    ErrorCode.REPORT_GENERATION_FAILED,
                                                    "Invalid report generation request")))
                    .bodyToMono(ReportDto.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Failed generating report for account: {}", input.accountId(), ex);
            throw FinFlowException.internalError(
                    ErrorCode.REPORT_GENERATION_FAILED, "Failed to generate report", ex);
        }
    }
}
