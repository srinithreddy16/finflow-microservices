package com.finflow.graphql.client.dto;

import java.time.Instant;

public record ReportDto(
        String id,
        String accountId,
        String name,
        String status,
        String downloadUrl,
        Instant generatedAt,
        Instant expiresAt,
        Instant createdAt,
        String reportType) {}
