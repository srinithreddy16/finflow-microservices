package com.finflow.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.report.dto.ReportRequestDto;
import com.finflow.report.dto.ReportResponseDto;
import com.finflow.report.exception.ReportGenerationException;
import com.finflow.report.exception.ReportNotFoundException;
import com.finflow.report.mapper.ReportMapper;
import com.finflow.report.model.Report;
import com.finflow.report.model.ReportFormat;
import com.finflow.report.model.ReportStatus;
import com.finflow.report.model.ReportType;
import com.finflow.report.repository.ReportRepository;
import com.finflow.report.service.CsvGeneratorService;
import com.finflow.report.service.PdfGeneratorService;
import com.finflow.report.service.ReportService;
import com.finflow.report.service.S3UploadService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private PdfGeneratorService pdfGeneratorService;
    @Mock private CsvGeneratorService csvGeneratorService;
    @Mock private S3UploadService s3UploadService;
    @Mock private ReportMapper reportMapper;

    @InjectMocks private ReportService reportService;

    @Test
    void generateReport_CreatesAndCompletesReport_WhenPdfRequested() {
        Report pendingSaved = Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build();
        Report generatingSaved = Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build();
        Report readySaved =
                Report.builder()
                        .id("rep-001")
                        .reportType(ReportType.TRANSACTION_HISTORY)
                        .status(ReportStatus.READY)
                        .generatedAt(Instant.now())
                        .build();

        when(reportRepository.save(any())).thenReturn(pendingSaved, generatingSaved, readySaved);
        when(pdfGeneratorService.generatePdf(any(), any())).thenReturn("pdf".getBytes());
        when(s3UploadService.buildS3Key(anyString(), anyString(), anyString(), any()))
                .thenReturn("reports/acc-001/tx/rep-001.pdf");
        when(s3UploadService.uploadFile(any(), anyString(), anyString()))
                .thenReturn("reports/acc-001/tx/rep-001.pdf");
        when(s3UploadService.generatePresignedUrl(anyString())).thenReturn("https://s3.amazonaws.com/x");
        when(reportMapper.toDto(any()))
                .thenReturn(
                        new ReportResponseDto(
                                "rep-001",
                                "acc-001",
                                "name",
                                "READY",
                                "TRANSACTION_HISTORY",
                                "PDF",
                                null,
                                100L,
                                LocalDate.now().minusDays(1),
                                LocalDate.now(),
                                null,
                                "corr",
                                Instant.now(),
                                Instant.now(),
                                Instant.now(),
                                null));

        ReportRequestDto request =
                new ReportRequestDto("acc-001", ReportType.TRANSACTION_HISTORY, ReportFormat.PDF, null, null, "c1");
        reportService.generateReport(request, "user-001");

        verify(reportRepository, atLeast(3)).save(any());
        verify(pdfGeneratorService).generatePdf(any(), any());
        verify(s3UploadService).uploadFile(any(), anyString(), anyString());
        verify(s3UploadService).generatePresignedUrl(anyString());
    }

    @Test
    void generateReport_SetsFailed_WhenS3UploadFails() {
        when(reportRepository.save(any()))
                .thenReturn(
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build(),
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build(),
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build());
        when(pdfGeneratorService.generatePdf(any(), any())).thenReturn("pdf".getBytes());
        when(s3UploadService.buildS3Key(anyString(), anyString(), anyString(), any()))
                .thenReturn("reports/acc-001/tx/rep-001.pdf");
        doThrow(new ReportGenerationException("S3 fail"))
                .when(s3UploadService)
                .uploadFile(any(), anyString(), anyString());

        ReportRequestDto request =
                new ReportRequestDto("acc-001", ReportType.TRANSACTION_HISTORY, ReportFormat.PDF, null, null, "c1");

        assertThatThrownBy(() -> reportService.generateReport(request, "user-001"))
                .isInstanceOf(ReportGenerationException.class);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, atLeast(1)).save(captor.capture());
        assertThat(captor.getAllValues().stream().anyMatch(r -> r.getStatus() == ReportStatus.FAILED)).isTrue();
    }

    @Test
    void generateReport_SetsFailed_WhenPdfGenerationFails() {
        when(reportRepository.save(any()))
                .thenReturn(
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build(),
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build(),
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build());
        when(pdfGeneratorService.generatePdf(any(), any())).thenThrow(new ReportGenerationException("PDF fail"));

        ReportRequestDto request =
                new ReportRequestDto("acc-001", ReportType.TRANSACTION_HISTORY, ReportFormat.PDF, null, null, "c1");

        assertThatThrownBy(() -> reportService.generateReport(request, "user-001"))
                .isInstanceOf(ReportGenerationException.class);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, atLeast(1)).save(captor.capture());
        assertThat(captor.getAllValues().stream().anyMatch(r -> r.getStatus() == ReportStatus.FAILED)).isTrue();
    }

    @Test
    void generateReport_UsesCsv_WhenFormatIsCsv() {
        when(reportRepository.save(any()))
                .thenReturn(
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build(),
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build(),
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).status(ReportStatus.READY).generatedAt(Instant.now()).build());
        when(csvGeneratorService.generateCsv(any(), any())).thenReturn("csv".getBytes());
        when(s3UploadService.buildS3Key(anyString(), anyString(), anyString(), any())).thenReturn("reports/acc-001/tx/rep-001.csv");
        when(s3UploadService.generatePresignedUrl(anyString())).thenReturn("https://s3.amazonaws.com/csv");
        when(reportMapper.toDto(any()))
                .thenReturn(new ReportResponseDto("rep-001", "acc-001", "name", "READY", "TRANSACTION_HISTORY", "CSV", null, 10L, LocalDate.now(), LocalDate.now(), null, "c1", Instant.now(), Instant.now(), Instant.now(), null));

        ReportRequestDto request =
                new ReportRequestDto("acc-001", ReportType.TRANSACTION_HISTORY, ReportFormat.CSV, null, null, "c1");
        reportService.generateReport(request, "user-001");

        verify(csvGeneratorService).generateCsv(any(), any());
        verify(pdfGeneratorService, never()).generatePdf(any(), any());
    }

    @Test
    void generateReport_DefaultsToLast30Days_WhenNoDatesProvided() {
        when(reportRepository.save(any()))
                .thenReturn(
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build(),
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).build(),
                        Report.builder().id("rep-001").reportType(ReportType.TRANSACTION_HISTORY).status(ReportStatus.READY).generatedAt(Instant.now()).build());
        when(pdfGeneratorService.generatePdf(any(), any())).thenReturn("pdf".getBytes());
        when(s3UploadService.buildS3Key(anyString(), anyString(), anyString(), any())).thenReturn("reports/acc-001/tx/rep-001.pdf");
        when(s3UploadService.generatePresignedUrl(anyString())).thenReturn("https://s3.amazonaws.com/x");
        when(reportMapper.toDto(any()))
                .thenReturn(new ReportResponseDto("rep-001", "acc-001", "name", "READY", "TRANSACTION_HISTORY", "PDF", null, 10L, LocalDate.now(), LocalDate.now(), null, "c1", Instant.now(), Instant.now(), Instant.now(), null));

        ReportRequestDto request =
                new ReportRequestDto("acc-001", ReportType.TRANSACTION_HISTORY, ReportFormat.PDF, null, null, "c1");
        reportService.generateReport(request, "user-001");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, atLeast(1)).save(captor.capture());
        Report firstSaved = captor.getAllValues().get(0);
        LocalDate expected = LocalDate.now().minusDays(30);
        assertThat(firstSaved.getFromDate()).isBetween(expected.minusDays(1), expected.plusDays(1));
    }

    @Test
    void getReport_GeneratesFreshUrl_WhenStatusIsReady() {
        Report report =
                Report.builder()
                        .id("rep-001")
                        .status(ReportStatus.READY)
                        .s3Key("reports/acc-001/tx/rep-001.pdf")
                        .build();
        when(reportRepository.findById("rep-001")).thenReturn(Optional.of(report));
        when(s3UploadService.generatePresignedUrl(report.getS3Key())).thenReturn("https://fresh-url.com");
        when(reportMapper.toDto(any()))
                .thenReturn(new ReportResponseDto("rep-001", "acc-001", "name", "READY", "TRANSACTION_HISTORY", "PDF", null, 10L, LocalDate.now(), LocalDate.now(), null, "c1", Instant.now(), Instant.now(), Instant.now(), null));

        reportService.getReport("rep-001");
        verify(s3UploadService).generatePresignedUrl("reports/acc-001/tx/rep-001.pdf");
    }

    @Test
    void getReport_ThrowsNotFoundException_WhenNotFound() {
        when(reportRepository.findById("non-existent")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reportService.getReport("non-existent"))
                .isInstanceOf(ReportNotFoundException.class);
    }
}
