package com.finflow.report.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ReportData(
        String accountId,
        String reportTitle,
        LocalDate fromDate,
        LocalDate toDate,
        List<Map<String, String>> tableHeaders,
        List<List<String>> tableRows,
        Map<String, String> summaryFields) {}
