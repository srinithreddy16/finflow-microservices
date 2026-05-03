package com.finflow.report.repository;

import com.finflow.report.model.Report;
import com.finflow.report.model.ReportStatus;
import com.finflow.report.model.ReportType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Report entities.
 *
 * <p>Note: this repository stores report METADATA only.
 *
 * <p>Actual report files are in S3.
 *
 * <p>The s3Key field is used to generate presigned download URLs via
 * S3UploadService.generatePresignedUrl(report.getS3Key())
 */
@Repository
public interface ReportRepository extends JpaRepository<Report, String> {

    List<Report> findByAccountIdOrderByCreatedAtDesc(String accountId);

    List<Report> findByAccountIdAndStatusOrderByCreatedAtDesc(String accountId, ReportStatus status);

    Page<Report> findByAccountId(String accountId, Pageable pageable);

    Page<Report> findByAccountIdAndStatus(String accountId, ReportStatus status, Pageable pageable);

    List<Report> findByAccountIdAndReportTypeOrderByCreatedAtDesc(String accountId, ReportType reportType);

    List<Report> findByStatusAndCreatedAtBefore(ReportStatus status, Instant cutoff);

    long countByAccountIdAndStatus(String accountId, ReportStatus status);

    Optional<Report> findFirstByAccountIdAndReportTypeAndStatusOrderByCreatedAtDesc(
            String accountId, ReportType reportType, ReportStatus status);
}
