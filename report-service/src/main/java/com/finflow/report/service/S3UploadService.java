package com.finflow.report.service;

import com.finflow.report.exception.ReportGenerationException;
import com.finflow.report.model.ReportFormat;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * Service for uploading reports to AWS S3 and generating presigned URLs.
 *
 * <p>S3 key structure: reports/{accountId}/{reportType}/{reportId}.{ext} Example:
 * reports/acc-001/transaction_history/abc-123.pdf
 *
 * <p>Presigned URLs:
 *
 * <p>- Generated on-demand (NOT stored in database)
 *
 * <p>- Expire after 60 minutes by default (configurable)
 *
 * <p>- Each call to generatePresignedUrl() generates a fresh URL
 *
 * <p>- The S3 key is stored in the Report entity for URL regeneration
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class S3UploadService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name:finflow-reports}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry-minutes:60}")
    private int expiryMinutes;

    public String uploadFile(byte[] fileContent, String s3Key, String contentType) {
        PutObjectRequest request =
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(contentType)
                        .contentLength((long) fileContent.length)
                        .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(fileContent));
            log.info(
                    "File uploaded to S3: bucket={}, key={}, size={} bytes",
                    bucketName,
                    s3Key,
                    fileContent.length);
            return s3Key;
        } catch (S3Exception | SdkClientException ex) {
            log.error("S3 upload failed: key={}", s3Key, ex);
            throw ReportGenerationException.s3Error(s3Key, ex);
        }
    }

    public String generatePresignedUrl(String s3Key) {
        try {
            GetObjectPresignRequest presignRequest =
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofMinutes(expiryMinutes))
                            .getObjectRequest(
                                    GetObjectRequest.builder().bucket(bucketName).key(s3Key).build())
                            .build();

            PresignedGetObjectRequest presignedRequest =
                    s3Presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();
            log.debug(
                    "Presigned URL generated for key: {}, expires in: {} minutes",
                    s3Key,
                    expiryMinutes);
            return url;
        } catch (Exception ex) {
            log.error("Failed to generate presigned URL: {}", s3Key, ex);
            throw ReportGenerationException.s3Error(s3Key, ex);
        }
    }

    public void deleteFile(String s3Key) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucketName).key(s3Key).build());
            log.info("File deleted from S3: {}", s3Key);
        } catch (Exception ex) {
            log.error("Failed to delete file from S3: {}", s3Key, ex);
        }
    }

    public boolean fileExists(String s3Key) {
        boolean exists = true;
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(s3Key).build());
        } catch (NoSuchKeyException ex) {
            exists = false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                exists = false;
            } else {
                throw ex;
            }
        }
        log.debug("S3 file exists check: key={}, exists={}", s3Key, exists);
        return exists;
    }

    public String buildS3Key(String accountId, String reportId, String reportType, ReportFormat format) {
        String extension = format == ReportFormat.PDF ? "pdf" : "csv";
        return String.format("reports/%s/%s/%s.%s", accountId, reportType.toLowerCase(), reportId, extension);
    }
}
