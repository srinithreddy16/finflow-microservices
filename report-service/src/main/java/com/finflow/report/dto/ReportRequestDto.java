package com.finflow.report.dto;

import com.finflow.report.model.ReportFormat;
import com.finflow.report.model.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Request to generate a new report. fromDate and toDate are optional. If not provided:
 *
 * <p>- TRANSACTION_HISTORY: defaults to last 30 days
 *
 * <p>- ANALYTICS_SUMMARY: defaults to current month
 *
 * <p>- FRAUD_REPORT: defaults to last 7 days
 */
public record ReportRequestDto(
        @NotBlank String accountId,
        @NotNull ReportType reportType,
        ReportFormat format,
        LocalDate fromDate,
        LocalDate toDate,
        String correlationId) {}
