package com.finflow.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Report Service -- generates and stores financial reports.
 *
 * <p>Triggered by:
 *
 * <p>- GraphQL gateway via REST: POST /api/reports
 *
 * <p>- Direct REST calls: GET /api/reports/{id}
 *
 * <p>Report generation flow:
 *
 * <p>1. REST request received (POST /api/reports)
 *
 * <p>2. Report record created in DB with status=PENDING
 *
 * <p>3. Report generation runs synchronously:
 *
 * <p>- PDF: Apache PDFBox renders financial data to PDF
 *
 * <p>- CSV: OpenCSV exports transaction data to CSV
 *
 * <p>4. Generated file uploaded to AWS S3
 *
 * <p>5. Presigned URL generated (1 hour expiry)
 *
 * <p>6. Report record updated with status=READY + downloadUrl
 *
 * <p>7. ReportResponseDto returned to caller
 *
 * <p>Storage:
 *
 * <p>- Report metadata: report_db (PostgreSQL)
 *
 * <p>- Report files: AWS S3 bucket (finflow-reports)
 *
 * <p>- Presigned URLs: generated on-demand, not stored
 *
 * <p>In production: report generation should be async (move to background job). For FinFlow,
 * synchronous generation is sufficient for the portfolio scope.
 *
 * <p>Port: 8089
 */
@SpringBootApplication
public class ReportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportServiceApplication.class, args);
    }
}
