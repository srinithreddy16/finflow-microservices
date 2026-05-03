package com.finflow.report.controller;

import com.finflow.common.dto.PagedResponse;
import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.common.util.IdGenerator;
import com.finflow.report.dto.ReportRequestDto;
import com.finflow.report.dto.ReportResponseDto;
import com.finflow.report.model.ReportStatus;
import com.finflow.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report REST API -- called by the GraphQL gateway.
 *
 * <p>POST /api/reports -> triggers synchronous report generation. The response is returned only
 * after generation completes (PDF/CSV is generated, uploaded to S3, presigned URL created).
 *
 * <p>GET /api/reports/{id} -> always returns a fresh presigned URL even if the report was
 * generated previously.
 *
 * <p>GET /api/reports/{id}/download-url -> convenience endpoint to get just the URL without the
 * full report metadata.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Generate a new report")
    public ReportResponseDto generateReport(
            @RequestBody @Valid ReportRequestDto request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String corrId) {
        if (corrId == null) {
            corrId = IdGenerator.correlationId();
        }
        String requestedBy = userId != null ? userId : "anonymous";

        ReportRequestDto requestWithCorrelationId =
                new ReportRequestDto(
                        request.accountId(),
                        request.reportType(),
                        request.format(),
                        request.fromDate(),
                        request.toDate(),
                        corrId);

        log.info(
                "Report generation requested: type={}, account={}",
                requestWithCorrelationId.reportType(),
                requestWithCorrelationId.accountId());
        return reportService.generateReport(requestWithCorrelationId, requestedBy);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get report by ID with fresh presigned URL")
    public ReportResponseDto getReportById(@PathVariable String id) {
        return reportService.getReport(id);
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get all reports for an account")
    public PagedResponse<ReportResponseDto> getReportsByAccount(
            @PathVariable String accountId,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reportService.getReportsByAccount(accountId, status, page, size);
    }

    @GetMapping("/{id}/download-url")
    @Operation(summary = "Get a fresh presigned download URL for a report")
    public Map<String, Object> getDownloadUrl(@PathVariable String id) {
        ReportResponseDto report = reportService.getReport(id);
        if (!ReportStatus.READY.name().equals(report.status())) {
            throw FinFlowException.badRequest(
                    ErrorCode.REPORT_NOT_FOUND,
                    "Report is not ready for download. Status: " + report.status());
        }
        return Map.of("downloadUrl", report.downloadUrl(), "expiresAt", report.expiresAt());
    }
}
