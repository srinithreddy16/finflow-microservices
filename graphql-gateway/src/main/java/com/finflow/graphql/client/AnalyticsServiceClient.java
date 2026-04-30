package com.finflow.graphql.client;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.graphql.client.dto.AnalyticsSummaryDto;
import com.finflow.graphql.client.dto.DailyMetricsDto;
import com.finflow.graphql.client.dto.PlatformSummaryDto;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class AnalyticsServiceClient {

    private final WebClient webClient;

    public AnalyticsServiceClient(
            @Value("${service.analytics.url:http://localhost:8088}") String analyticsUrl,
            WebClient.Builder webClientBuilder) {
        this.webClient = WebClient.create(analyticsUrl);
    }

    public AnalyticsSummaryDto getSummary(String accountId, LocalDate from, LocalDate to) {
        log.debug("Fetching analytics summary for account: {}", accountId);
        try {
            return webClient
                    .get()
                    .uri(
                            uriBuilder ->
                                    uriBuilder.path("/api/analytics/summary/{accountId}")
                                            .queryParam("fromDate", from)
                                            .queryParam("toDate", to)
                                            .build(accountId))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response ->
                                    Mono.error(
                                            FinFlowException.notFound(
                                                    ErrorCode.RESOURCE_NOT_FOUND,
                                                    "Analytics summary not found for account: "
                                                            + accountId)))
                    .bodyToMono(AnalyticsSummaryDto.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Failed fetching analytics summary for account: {}", accountId, ex);
            throw FinFlowException.internalError(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Failed to fetch analytics summary",
                    ex);
        }
    }

    public DailyMetricsDto getDailyMetrics(String accountId, LocalDate date) {
        try {
            return webClient
                    .get()
                    .uri(
                            uriBuilder ->
                                    uriBuilder.path("/api/analytics/daily/{accountId}")
                                            .queryParam("date", date)
                                            .build(accountId))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response ->
                                    Mono.error(
                                            FinFlowException.notFound(
                                                    ErrorCode.RESOURCE_NOT_FOUND,
                                                    "Daily metrics not found for account: " + accountId)))
                    .bodyToMono(DailyMetricsDto.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Failed fetching daily metrics for account: {}", accountId, ex);
            throw FinFlowException.internalError(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Failed to fetch daily metrics",
                    ex);
        }
    }

    public List<DailyMetricsDto> getAccountHistory(String accountId, int days) {
        try {
            return webClient
                    .get()
                    .uri(
                            uriBuilder ->
                                    uriBuilder.path("/api/analytics/history/{accountId}")
                                            .queryParam("days", days)
                                            .build(accountId))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response ->
                                    Mono.error(
                                            FinFlowException.notFound(
                                                    ErrorCode.RESOURCE_NOT_FOUND,
                                                    "Analytics history not found for account: "
                                                            + accountId)))
                    .bodyToFlux(DailyMetricsDto.class)
                    .collectList()
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Failed fetching account history for account: {}", accountId, ex);
            throw FinFlowException.internalError(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Failed to fetch analytics history",
                    ex);
        }
    }

    public PlatformSummaryDto getPlatformSummary() {
        try {
            return webClient
                    .get()
                    .uri("/api/analytics/platform")
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response ->
                                    Mono.error(
                                            FinFlowException.notFound(
                                                    ErrorCode.RESOURCE_NOT_FOUND,
                                                    "Platform summary not found")))
                    .bodyToMono(PlatformSummaryDto.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Failed fetching platform summary", ex);
            throw FinFlowException.internalError(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "Failed to fetch platform summary",
                    ex);
        }
    }
}
