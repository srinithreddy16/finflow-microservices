package com.finflow.analytics.controller;

import com.finflow.analytics.dto.AnalyticsSummaryDto;
import com.finflow.analytics.dto.DailyMetricsDto;
import com.finflow.analytics.dto.PlatformSummaryDto;
import com.finflow.analytics.service.AnalyticsQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics REST API called by the GraphQL gateway. These endpoints are NOT directly exposed to
 * end users -- they are internal endpoints consumed by graphql-gateway via the
 * AnalyticsServiceClient.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping("/summary/{accountId}")
    public AnalyticsSummaryDto getSummary(
            @PathVariable String accountId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate) {
        if (fromDate == null) {
            fromDate = LocalDate.now().minusDays(30);
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }
        return analyticsQueryService.getSummary(accountId, fromDate, toDate);
    }

    @GetMapping("/daily/{accountId}")
    public DailyMetricsDto getDailyMetrics(
            @PathVariable String accountId,
            @RequestParam(required = false) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return analyticsQueryService.getDailyMetrics(accountId, date);
    }

    @GetMapping("/history/{accountId}")
    public List<DailyMetricsDto> getHistory(
            @PathVariable String accountId, @RequestParam(defaultValue = "30") int days) {
        return analyticsQueryService.getAccountHistory(accountId, days);
    }

    @GetMapping("/platform")
    @PreAuthorize("hasRole('BUSINESS_ADMIN') or hasRole('FINANCE_ANALYST')")
    public PlatformSummaryDto getPlatformSummary() {
        return analyticsQueryService.getPlatformSummary();
    }
}
