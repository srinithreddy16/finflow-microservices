package com.finflow.report.model;

public enum ReportStatus {
    PENDING("Report request received, awaiting generation"),
    GENERATING("Report is being generated"),
    READY("Report generated and available for download"),
    FAILED("Report generation failed");

    private final String description;

    ReportStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }

    public boolean isDownloadable() {
        return this == READY;
    }
}
