package com.finflow.report.exception;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import org.springframework.http.HttpStatus;

public class ReportNotFoundException extends FinFlowException {

    public ReportNotFoundException(String reportId) {
        super(
                ErrorCode.REPORT_NOT_FOUND,
                "Report not found with id: " + reportId,
                HttpStatus.NOT_FOUND);
    }
}
