package com.finflow.graphql.client.dto;

import java.time.LocalDate;

public record GenerateReportInputDto(
        String accountId, String reportType, LocalDate fromDate, LocalDate toDate, String format) {}
