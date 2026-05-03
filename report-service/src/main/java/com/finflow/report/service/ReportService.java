package com.finflow.report.service;

import com.finflow.common.dto.PagedResponse;
import com.finflow.report.dto.ReportRequestDto;
import com.finflow.report.dto.ReportResponseDto;
import com.finflow.report.exception.ReportGenerationException;
import com.finflow.report.exception.ReportNotFoundException;
import com.finflow.report.mapper.ReportMapper;
import com.finflow.report.model.Report;
import com.finflow.report.model.ReportFormat;
import com.finflow.report.model.ReportStatus;
import com.finflow.report.model.ReportType;
import com.finflow.report.repository.ReportRepository;
import com.finflow.report.service.ReportData;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Orchestrates synchronous report generation in a single request lifecycle:
 * request -> metadata persisted -> file generated -> uploaded to S3 -> presigned URL returned.
 *
 * <p>This approach is acceptable for the FinFlow portfolio scope. In production, large reports
 * should be generated asynchronously via background jobs/queues to avoid long-running request
 * timeouts and to improve throughput.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private static final String REPORT_BUCKET = "finflow-reports";

    private final ReportRepository reportRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final CsvGeneratorService csvGeneratorService;
    private final S3UploadService s3UploadService;
    private final ReportMapper reportMapper;

    @Transactional
    public ReportResponseDto generateReport(ReportRequestDto request, String requestedBy) {
        Report report = null;
        try {
            ReportFormat format = request.format() != null ? request.format() : ReportFormat.PDF;

            LocalDate fromDate = request.fromDate();
            LocalDate toDate = request.toDate();
            if (fromDate == null) {
                fromDate =
                        switch (request.reportType()) {
                            case TRANSACTION_HISTORY -> LocalDate.now().minusDays(30);
                            case ANALYTICS_SUMMARY -> LocalDate.now().withDayOfMonth(1);
                            case FRAUD_REPORT -> LocalDate.now().minusDays(7);
                        };
            }
            if (toDate == null) {
                toDate = LocalDate.now();
            }

            String name = generateReportName(request.reportType(), fromDate, toDate);

            report =
                    Report.builder()
                            .accountId(request.accountId())
                            .name(name)
                            .status(ReportStatus.PENDING)
                            .reportType(request.reportType())
                            .reportFormat(format)
                            .fromDate(fromDate)
                            .toDate(toDate)
                            .correlationId(request.correlationId())
                            .requestedBy(requestedBy)
                            .build();
            report = reportRepository.save(report);
            log.info("Report {} created with status PENDING", report.getId());

            report.setStatus(ReportStatus.GENERATING);
            report = reportRepository.save(report);

            ReportData reportData =
                    buildReportData(report, request.accountId(), fromDate, toDate);

            byte[] fileBytes =
                    switch (format) {
                        case PDF -> pdfGeneratorService.generatePdf(report, reportData);
                        case CSV -> csvGeneratorService.generateCsv(report, reportData);
                    };

            String s3Key =
                    s3UploadService.buildS3Key(
                            request.accountId(), report.getId(), request.reportType().name(), format);

            String contentType =
                    format == ReportFormat.PDF ? "application/pdf" : "text/csv;charset=UTF-8";

            s3UploadService.uploadFile(fileBytes, s3Key, contentType);
            String presignedUrl = s3UploadService.generatePresignedUrl(s3Key);

            Instant generatedAt = Instant.now();
            report.setStatus(ReportStatus.READY);
            report.setS3Key(s3Key);
            report.setS3Bucket(REPORT_BUCKET);
            report.setFileSizeBytes((long) fileBytes.length);
            report.setGeneratedAt(generatedAt);
            report = reportRepository.save(report);
            log.info("Report {} READY: size={} bytes, s3Key={}", report.getId(), fileBytes.length, s3Key);

            ReportResponseDto dto = reportMapper.toDto(report);
            return withDownload(dto, presignedUrl, generatedAt.plus(Duration.ofHours(1)));
        } catch (ReportGenerationException ex) {
            if (report != null) {
                report.setStatus(ReportStatus.FAILED);
                report.setErrorMessage(ex.getMessage());
                reportRepository.save(report);
                log.error("Report {} generation FAILED: {}", report.getId(), ex.getMessage(), ex);
            }
            throw ex;
        } catch (Exception ex) {
            if (report != null) {
                report.setStatus(ReportStatus.FAILED);
                report.setErrorMessage(ex.getMessage());
                reportRepository.save(report);
                log.error("Report {} generation FAILED: {}", report.getId(), ex.getMessage(), ex);
            }
            throw new ReportGenerationException("Report generation failed", ex);
        }
    }

    public ReportResponseDto getReport(String reportId) {
        Report report =
                reportRepository.findById(reportId).orElseThrow(() -> new ReportNotFoundException(reportId));

        ReportResponseDto dto = reportMapper.toDto(report);
        if (report.getStatus() == ReportStatus.READY
                && report.getS3Key() != null
                && !report.getS3Key().isBlank()) {
            String freshUrl = s3UploadService.generatePresignedUrl(report.getS3Key());
            return withDownload(dto, freshUrl, Instant.now().plus(Duration.ofHours(1)));
        }
        return dto;
    }

    public PagedResponse<ReportResponseDto> getReportsByAccount(
            String accountId, ReportStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Report> reportPage =
                status == null
                        ? reportRepository.findByAccountId(accountId, pageable)
                        : reportRepository.findByAccountIdAndStatus(accountId, status, pageable);

        List<ReportResponseDto> content = reportPage.getContent().stream().map(reportMapper::toDto).toList();
        return PagedResponse.from(reportPage, content);
    }

    private String generateReportName(ReportType type, LocalDate from, LocalDate to) {
        return switch (type) {
            case TRANSACTION_HISTORY -> "Transaction History: " + from + " to " + to;
            case ANALYTICS_SUMMARY -> "Analytics Summary: " + from + " to " + to;
            case FRAUD_REPORT -> "Fraud Report: " + from + " to " + to;
        };
    }

    private ReportData buildReportData(
            Report report, String accountId, LocalDate fromDate, LocalDate toDate) {
        List<Map<String, String>> tableHeaders;
        List<List<String>> tableRows;
        Map<String, String> summaryFields;

        switch (report.getReportType()) {
            case TRANSACTION_HISTORY -> {
                tableHeaders =
                        List.of(
                                Map.of("key", "id", "label", "Transaction ID"),
                                Map.of("key", "date", "label", "Date"),
                                Map.of("key", "amount", "label", "Amount"),
                                Map.of("key", "currency", "label", "Currency"),
                                Map.of("key", "status", "label", "Status"),
                                Map.of("key", "description", "label", "Description"));
                tableRows = generateSampleTransactionRows(10);
                summaryFields =
                        Map.of(
                                "Total Transactions", "10",
                                "Completed", "8",
                                "Failed", "2",
                                "Total Volume", "$4,250.00");
            }
            case ANALYTICS_SUMMARY -> {
                tableHeaders =
                        List.of(
                                Map.of("key", "date", "label", "Date"),
                                Map.of("key", "transactions", "label", "Transactions"),
                                Map.of("key", "volume", "label", "Volume"),
                                Map.of("key", "fraud", "label", "Fraud Flags"));
                tableRows = generateSampleAnalyticsRows(7);
                summaryFields =
                        Map.of(
                                "Period", fromDate + " to " + toDate,
                                "Total Volume", "$29,750.00",
                                "Fraud Rate", "2.5%");
            }
            case FRAUD_REPORT -> {
                tableHeaders =
                        List.of(
                                Map.of("key", "txId", "label", "Transaction ID"),
                                Map.of("key", "date", "label", "Date"),
                                Map.of("key", "score", "label", "Fraud Score"),
                                Map.of("key", "flagged", "label", "Flagged"),
                                Map.of("key", "reason", "label", "Reason"));
                tableRows = generateSampleFraudRows(5);
                summaryFields = Map.of("Total Flags", "5", "High Risk", "2");
            }
            default -> throw new ReportGenerationException("Unsupported report type: " + report.getReportType());
        }

        return new ReportData(
                accountId, report.getName(), fromDate, toDate, tableHeaders, tableRows, summaryFields);
    }

    private List<List<String>> generateSampleTransactionRows(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(
                        i -> {
                            double amount = ThreadLocalRandom.current().nextDouble(50.0, 5000.01);
                            String status = ThreadLocalRandom.current().nextBoolean() ? "COMPLETED" : "FAILED";
                            return List.of(
                                    UUID.randomUUID().toString().substring(0, 8),
                                    LocalDate.now().minusDays(i).toString(),
                                    String.format("$%.2f", amount),
                                    "USD",
                                    status,
                                    "Sample payment");
                        })
                .toList();
    }

    private List<List<String>> generateSampleAnalyticsRows(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(
                        i ->
                                List.of(
                                        LocalDate.now().minusDays(i).toString(),
                                        String.valueOf(ThreadLocalRandom.current().nextInt(12, 60)),
                                        String.format("$%.2f", ThreadLocalRandom.current().nextDouble(1200, 8000)),
                                        String.valueOf(ThreadLocalRandom.current().nextInt(0, 4))))
                .toList();
    }

    private List<List<String>> generateSampleFraudRows(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(
                        i ->
                                List.of(
                                        UUID.randomUUID().toString().substring(0, 8),
                                        LocalDate.now().minusDays(i).toString(),
                                        String.valueOf(ThreadLocalRandom.current().nextInt(65, 100)),
                                        i % 2 == 0 ? "YES" : "NO",
                                        i % 2 == 0 ? "Velocity spike detected" : "Geo mismatch"))
                .toList();
    }

    private ReportResponseDto withDownload(ReportResponseDto dto, String downloadUrl, Instant expiresAt) {
        return new ReportResponseDto(
                dto.id(),
                dto.accountId(),
                dto.name(),
                dto.status(),
                dto.reportType(),
                dto.reportFormat(),
                downloadUrl,
                dto.fileSizeBytes(),
                dto.fromDate(),
                dto.toDate(),
                dto.errorMessage(),
                dto.correlationId(),
                dto.createdAt(),
                dto.updatedAt(),
                dto.generatedAt(),
                expiresAt);
    }
}
