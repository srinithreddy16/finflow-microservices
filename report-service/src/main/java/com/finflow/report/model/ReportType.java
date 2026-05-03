package com.finflow.report.model;

public enum ReportType {
    TRANSACTION_HISTORY("Full transaction history for a date range"),
    ANALYTICS_SUMMARY("Aggregated analytics summary"),
    FRAUD_REPORT("Fraud detection events and scores");

    private final String description;

    ReportType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
