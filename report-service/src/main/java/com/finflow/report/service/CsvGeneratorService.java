package com.finflow.report.service;

import com.finflow.report.exception.ReportGenerationException;
import com.finflow.report.model.Report;
import com.finflow.report.service.ReportData;
import com.opencsv.CSVWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * CSV report generator using OpenCSV.
 *
 * <p>Output format:
 *
 * <p>- UTF-8 with BOM (for Excel compatibility -- Excel needs BOM to correctly detect UTF-8
 * encoding)
 *
 * <p>- Metadata rows at the top (company name, account, date range)
 *
 * <p>- Column headers row
 *
 * <p>- Data rows
 *
 * <p>- Summary section at the bottom
 *
 * <p>The BOM (EF BB BF) is prepended to ensure Excel opens the file correctly without encoding
 * issues on Windows.
 */
@Service
@Slf4j
public class CsvGeneratorService {

    public byte[] generateCsv(Report report, ReportData data) {
        log.info("Generating CSV for report: {}, type: {}", report.getId(), report.getReportType());

        try {
            StringWriter stringWriter = new StringWriter();
            CSVWriter csvWriter =
                    new CSVWriter(
                            stringWriter,
                            CSVWriter.DEFAULT_SEPARATOR,
                            CSVWriter.DEFAULT_QUOTE_CHARACTER,
                            CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                            CSVWriter.DEFAULT_LINE_END);

            csvWriter.writeNext(
                    new String[] {"FinFlow Financial Platform - " + valueOrEmpty(data.reportTitle())});
            csvWriter.writeNext(new String[] {"Account ID: " + valueOrEmpty(data.accountId())});
            csvWriter.writeNext(
                    new String[] {
                        "Period: "
                                + valueOrEmpty(data.fromDate() != null ? data.fromDate().toString() : "")
                                + " to "
                                + valueOrEmpty(data.toDate() != null ? data.toDate().toString() : "")
                    });
            csvWriter.writeNext(new String[] {"Generated: " + Instant.now()});
            csvWriter.writeNext(new String[] {});

            if (data.tableHeaders() != null && !data.tableHeaders().isEmpty()) {
                String[] headers =
                        data.tableHeaders().stream()
                                .map(h -> h.getOrDefault("label", h.getOrDefault("key", "")))
                                .toArray(String[]::new);
                csvWriter.writeNext(headers);
            }

            if (data.tableRows() != null) {
                for (java.util.List<String> row : data.tableRows()) {
                    csvWriter.writeNext(row.toArray(String[]::new));
                }
            }

            csvWriter.writeNext(new String[] {});
            csvWriter.writeNext(new String[] {"Summary"});
            if (data.summaryFields() != null) {
                for (java.util.Map.Entry<String, String> entry : data.summaryFields().entrySet()) {
                    csvWriter.writeNext(
                            new String[] {valueOrEmpty(entry.getKey()), valueOrEmpty(entry.getValue())});
                }
            }

            csvWriter.close();

            byte[] csvBytes = stringWriter.toString().getBytes(StandardCharsets.UTF_8);
            byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] result = new byte[bom.length + csvBytes.length];
            System.arraycopy(bom, 0, result, 0, bom.length);
            System.arraycopy(csvBytes, 0, result, bom.length, csvBytes.length);

            log.info("CSV generated: {} bytes for report: {}", result.length, report.getId());
            return result;
        } catch (Exception ex) {
            log.error("CSV generation failed: {}", report.getId(), ex);
            throw ReportGenerationException.csvError(report.getId(), ex);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
