package com.finflow.report.model;

public enum ReportFormat {
    PDF("PDF document"),
    CSV("CSV spreadsheet");

    private final String description;

    ReportFormat(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
