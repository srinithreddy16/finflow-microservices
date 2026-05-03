package com.finflow.report.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import org.springframework.http.HttpStatus;

public class ReportGenerationException extends FinFlowException {

    public ReportGenerationException(String message) {
        super(ErrorCode.REPORT_GENERATION_FAILED, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public ReportGenerationException(String message, Throwable cause) {
        super(ErrorCode.REPORT_GENERATION_FAILED, message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public static ReportGenerationException pdfError(String reportId, Throwable cause) {
        return new ReportGenerationException("PDF generation failed for report: " + reportId, cause);
    }

    public static ReportGenerationException csvError(String reportId, Throwable cause) {
        return new ReportGenerationException("CSV generation failed for report: " + reportId, cause);
    }

    public static ReportGenerationException s3Error(String reportId, Throwable cause) {
        return new ReportGenerationException("S3 upload failed for report: " + reportId, cause);
    }
}
