package com.finflow.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.finflow.report.model.Report;
import com.finflow.report.model.ReportFormat;
import com.finflow.report.model.ReportStatus;
import com.finflow.report.model.ReportType;
import com.finflow.report.service.PdfGeneratorService;
import com.finflow.report.service.ReportData;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PdfGeneratorServiceTest {

    @InjectMocks private PdfGeneratorService pdfGeneratorService;

    @Test
    void generatePdf_ReturnsByteArray_WhenDataIsValid() {
        Report report =
                Report.builder()
                        .id("rep-001")
                        .accountId("acc-001")
                        .name("Transaction History")
                        .status(ReportStatus.GENERATING)
                        .reportType(ReportType.TRANSACTION_HISTORY)
                        .reportFormat(ReportFormat.PDF)
                        .build();

        ReportData data =
                new ReportData(
                        "acc-001",
                        "Transaction History",
                        LocalDate.now().minusDays(30),
                        LocalDate.now(),
                        List.of(
                                Map.of("key", "id", "label", "Transaction ID"),
                                Map.of("key", "date", "label", "Date"),
                                Map.of("key", "amount", "label", "Amount"),
                                Map.of("key", "status", "label", "Status")),
                        List.of(
                                List.of("tx-001", LocalDate.now().minusDays(2).toString(), "$500.00", "COMPLETED"),
                                List.of("tx-002", LocalDate.now().minusDays(1).toString(), "$700.00", "FAILED"),
                                List.of("tx-003", LocalDate.now().toString(), "$300.00", "COMPLETED")),
                        Map.of("Total", "3", "Volume", "$1500"));

        byte[] result = pdfGeneratorService.generatePdf(report, data);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
        assertThat(new String(Arrays.copyOf(result, 4))).isEqualTo("%PDF");
    }

    @Test
    void generatePdf_HandlesEmptyTableRows() {
        Report report =
                Report.builder()
                        .id("rep-001")
                        .reportType(ReportType.ANALYTICS_SUMMARY)
                        .reportFormat(ReportFormat.PDF)
                        .build();

        ReportData data =
                new ReportData(
                        "acc-001",
                        "Analytics Summary",
                        LocalDate.now().minusDays(7),
                        LocalDate.now(),
                        List.of(Map.of("key", "date", "label", "Date")),
                        List.of(),
                        Map.of("Total", "0"));

        byte[] result = pdfGeneratorService.generatePdf(report, data);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    void generatePdf_HandlesLongTextTruncation() {
        Report report =
                Report.builder()
                        .id("rep-001")
                        .reportType(ReportType.TRANSACTION_HISTORY)
                        .reportFormat(ReportFormat.PDF)
                        .build();

        String longText =
                "This is a very long description that should be truncated to prevent table cell overflow in the generated PDF report output.";
        ReportData data =
                new ReportData(
                        "acc-001",
                        "Transaction History",
                        LocalDate.now().minusDays(10),
                        LocalDate.now(),
                        List.of(
                                Map.of("key", "id", "label", "Transaction ID"),
                                Map.of("key", "description", "label", "Description")),
                        List.of(List.of("tx-xyz", longText)),
                        Map.of("Rows", "1"));

        byte[] result = pdfGeneratorService.generatePdf(report, data);
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }
}
