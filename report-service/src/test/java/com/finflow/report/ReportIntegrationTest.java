package com.finflow.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.finflow.report.dto.ReportRequestDto;
import com.finflow.report.dto.ReportResponseDto;
import com.finflow.report.model.ReportFormat;
import com.finflow.report.model.ReportType;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
        })
class ReportIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("report_db")
                    .withUsername("finflow")
                    .withPassword("finflow");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockBean private S3Client s3Client;
    @MockBean private S3Presigner s3Presigner;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void generateAndRetrieveReport_FullFlow() throws Exception {
        mockS3();

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "itest-user");
        HttpEntity<ReportRequestDto> requestEntity =
                new HttpEntity<>(new ReportRequestDto("acc-it-1", ReportType.TRANSACTION_HISTORY, null, null, null, null), headers);

        ResponseEntity<ReportResponseDto> createResponse =
                restTemplate.postForEntity("/api/reports", requestEntity, ReportResponseDto.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(createResponse.getBody()).isNotNull();
        String reportId = createResponse.getBody().id();

        String status =
                jdbcTemplate.queryForObject(
                        "SELECT status FROM reports WHERE id = ?",
                        String.class,
                        reportId);
        assertThat(status).isEqualTo("READY");

        ResponseEntity<ReportResponseDto> getResponse =
                restTemplate.getForEntity("/api/reports/" + reportId, ReportResponseDto.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().downloadUrl()).isNotBlank();
    }

    @Test
    void generateCsvReport_FullFlow() throws Exception {
        mockS3();

        ResponseEntity<ReportResponseDto> createResponse =
                restTemplate.postForEntity(
                        "/api/reports",
                        new ReportRequestDto("acc-csv-1", ReportType.TRANSACTION_HISTORY, ReportFormat.CSV, null, null, null),
                        ReportResponseDto.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String reportId = createResponse.getBody().id();

        String format =
                jdbcTemplate.queryForObject(
                        "SELECT report_format FROM reports WHERE id = ?",
                        String.class,
                        reportId);
        assertThat(format).isEqualTo("CSV");
    }

    @Test
    void getReportsByAccount_ReturnsList() throws Exception {
        mockS3();

        restTemplate.postForEntity(
                "/api/reports",
                new ReportRequestDto("acc-list-1", ReportType.TRANSACTION_HISTORY, ReportFormat.PDF, null, null, null),
                ReportResponseDto.class);
        restTemplate.postForEntity(
                "/api/reports",
                new ReportRequestDto("acc-list-1", ReportType.ANALYTICS_SUMMARY, ReportFormat.CSV, null, null, null),
                ReportResponseDto.class);

        ResponseEntity<Map> response =
                restTemplate.getForEntity("/api/reports/account/acc-list-1", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("content")).asList().hasSize(2);
    }

    private void mockS3() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://mock-s3-url.com").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
    }
}
