package com.finflow.report.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ReportResponseDto(
        String id,
        String accountId,
        String name,
        String status,
        String reportType,
        String reportFormat,
        String downloadUrl,
        Long fileSizeBytes,
        LocalDate fromDate,
        LocalDate toDate,
        String errorMessage,
        String correlationId,
        Instant createdAt,
        Instant updatedAt,
        Instant generatedAt,
        Instant expiresAt) {}
