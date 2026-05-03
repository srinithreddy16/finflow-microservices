package com.finflow.report.service;

import com.finflow.report.exception.ReportGenerationException;
import com.finflow.report.model.Report;
import com.finflow.report.service.ReportData;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

/**
 * PDF report generator using Apache PDFBox.
 *
 * <p>Generated PDF structure:
 *
 * <p>1. Header: company name, report title, date range, account ID
 *
 * <p>2. Summary section: key metrics (totals, counts)
 *
 * <p>3. Data table: the actual report data with column headers
 *
 * <p>Multi-page support: automatically adds new pages when the content exceeds the page height.
 *
 * <p>PDFBox coordinate system: origin (0,0) is at bottom-left. A4 page: width=595 points,
 * height=842 points. 1 point = 1/72 inch.
 */
@Service
@Slf4j
public class PdfGeneratorService {

    private static final float PAGE_MARGIN = 50f;
    private static final float LINE_SPACING = 14f;
    private static final float TABLE_ROW_HEIGHT = 18f;

    public byte[] generatePdf(Report report, ReportData data) {
        log.info("Generating PDF for report: {}, type: {}", report.getId(), report.getReportType());

        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float y = pageHeight - PAGE_MARGIN;

            // Header section
            float companyFontSize = 18f;
            String company = "FinFlow Financial Platform";
            contentStream.beginText();
            contentStream.setFont(titleFont, companyFontSize);
            contentStream.newLineAtOffset((pageWidth - textWidth(titleFont, company, companyFontSize)) / 2f, y);
            contentStream.showText(company);
            contentStream.endText();
            y -= LINE_SPACING + 8;

            float titleSize = 14f;
            contentStream.beginText();
            contentStream.setFont(headerFont, titleSize);
            contentStream.newLineAtOffset(PAGE_MARGIN, y);
            contentStream.showText(truncateText(data.reportTitle(), 80));
            contentStream.endText();
            y -= LINE_SPACING;

            float bodySize = 10f;
            contentStream.beginText();
            contentStream.setFont(bodyFont, bodySize);
            contentStream.newLineAtOffset(PAGE_MARGIN, y);
            contentStream.showText("Period: " + data.fromDate() + " to " + data.toDate());
            contentStream.endText();
            y -= LINE_SPACING;

            contentStream.beginText();
            contentStream.setFont(bodyFont, bodySize);
            contentStream.newLineAtOffset(PAGE_MARGIN, y);
            contentStream.showText("Generated: " + Instant.now());
            contentStream.endText();
            y -= LINE_SPACING;

            contentStream.beginText();
            contentStream.setFont(bodyFont, bodySize);
            contentStream.newLineAtOffset(PAGE_MARGIN, y);
            contentStream.showText("Account: " + data.accountId());
            contentStream.endText();
            y -= 8;

            drawHorizontalLine(contentStream, PAGE_MARGIN, y, pageWidth - (PAGE_MARGIN * 2));
            y -= LINE_SPACING;

            // Summary section
            if (data.summaryFields() != null && !data.summaryFields().isEmpty()) {
                contentStream.beginText();
                contentStream.setFont(headerFont, 12f);
                contentStream.newLineAtOffset(PAGE_MARGIN, y);
                contentStream.showText("Summary");
                contentStream.endText();
                y -= LINE_SPACING;

                List<Map.Entry<String, String>> entries = new ArrayList<>(data.summaryFields().entrySet());
                for (int i = 0; i < entries.size(); i += 2) {
                    if (y < PAGE_MARGIN) {
                        contentStream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        pageHeight = page.getMediaBox().getHeight();
                        y = pageHeight - PAGE_MARGIN;
                    }

                    Map.Entry<String, String> left = entries.get(i);
                    String leftText = truncateText(left.getKey() + ": " + left.getValue(), 40);
                    contentStream.beginText();
                    contentStream.setFont(bodyFont, bodySize);
                    contentStream.newLineAtOffset(PAGE_MARGIN, y);
                    contentStream.showText(leftText);
                    contentStream.endText();

                    if (i + 1 < entries.size()) {
                        Map.Entry<String, String> right = entries.get(i + 1);
                        String rightText = truncateText(right.getKey() + ": " + right.getValue(), 40);
                        contentStream.beginText();
                        contentStream.setFont(bodyFont, bodySize);
                        contentStream.newLineAtOffset(pageWidth / 2f + 10f, y);
                        contentStream.showText(rightText);
                        contentStream.endText();
                    }
                    y -= LINE_SPACING;
                }

                drawHorizontalLine(contentStream, PAGE_MARGIN, y, pageWidth - (PAGE_MARGIN * 2));
                y -= LINE_SPACING;
            }

            // Table section
            if (data.tableHeaders() != null
                    && !data.tableHeaders().isEmpty()
                    && data.tableRows() != null
                    && !data.tableRows().isEmpty()) {
                List<String> headerLabels = extractHeaderLabels(data.tableHeaders());
                int columnCount = Math.max(headerLabels.size(), 1);
                float tableWidth = pageWidth - (PAGE_MARGIN * 2);
                float colWidth = tableWidth / columnCount;

                y = drawTableHeader(contentStream, headerLabels, y, colWidth, tableWidth, headerFont);

                for (int rowIndex = 0; rowIndex < data.tableRows().size(); rowIndex++) {
                    if (y < PAGE_MARGIN + TABLE_ROW_HEIGHT) {
                        contentStream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        pageHeight = page.getMediaBox().getHeight();
                        y = pageHeight - PAGE_MARGIN;
                        y = drawTableHeader(contentStream, headerLabels, y, colWidth, tableWidth, headerFont);
                    }

                    if (rowIndex % 2 == 1) {
                        contentStream.setNonStrokingColor(new Color(245, 245, 245));
                        contentStream.addRect(PAGE_MARGIN, y - TABLE_ROW_HEIGHT + 4, tableWidth, TABLE_ROW_HEIGHT);
                        contentStream.fill();
                        contentStream.setNonStrokingColor(Color.BLACK);
                    }

                    List<String> row = data.tableRows().get(rowIndex);
                    for (int col = 0; col < columnCount; col++) {
                        String cell =
                                col < row.size() && row.get(col) != null ? truncateText(row.get(col), 24) : "";
                        contentStream.beginText();
                        contentStream.setFont(bodyFont, 9f);
                        contentStream.newLineAtOffset(PAGE_MARGIN + (col * colWidth) + 4, y - 11);
                        contentStream.showText(cell);
                        contentStream.endText();
                    }

                    contentStream.setStrokingColor(Color.GRAY);
                    contentStream.addRect(PAGE_MARGIN, y - TABLE_ROW_HEIGHT + 4, tableWidth, TABLE_ROW_HEIGHT);
                    contentStream.stroke();

                    y -= TABLE_ROW_HEIGHT;
                }
            }

            contentStream.close();
            document.save(baos);
            byte[] bytes = baos.toByteArray();
            log.info("PDF generated: {} bytes for report: {}", bytes.length, report.getId());
            return bytes;
        } catch (IOException ex) {
            log.error("PDF generation failed: {}", report.getId(), ex);
            throw ReportGenerationException.pdfError(report.getId(), ex);
        }
    }

    private float drawTableHeader(
            PDPageContentStream contentStream,
            List<String> headerLabels,
            float y,
            float colWidth,
            float tableWidth,
            PDFont headerFont)
            throws IOException {
        contentStream.setNonStrokingColor(Color.LIGHT_GRAY);
        contentStream.addRect(PAGE_MARGIN, y - TABLE_ROW_HEIGHT + 4, tableWidth, TABLE_ROW_HEIGHT);
        contentStream.fill();
        contentStream.setNonStrokingColor(Color.BLACK);

        for (int col = 0; col < headerLabels.size(); col++) {
            contentStream.beginText();
            contentStream.setFont(headerFont, 10f);
            contentStream.newLineAtOffset(PAGE_MARGIN + (col * colWidth) + 4, y - 11);
            contentStream.showText(truncateText(headerLabels.get(col), 24));
            contentStream.endText();
        }

        contentStream.setStrokingColor(Color.DARK_GRAY);
        contentStream.addRect(PAGE_MARGIN, y - TABLE_ROW_HEIGHT + 4, tableWidth, TABLE_ROW_HEIGHT);
        contentStream.stroke();
        return y - TABLE_ROW_HEIGHT;
    }

    private List<String> extractHeaderLabels(List<Map<String, String>> tableHeaders) {
        List<String> labels = new ArrayList<>();
        for (Map<String, String> headerMap : tableHeaders) {
            if (headerMap != null && !headerMap.isEmpty()) {
                Map.Entry<String, String> first = headerMap.entrySet().iterator().next();
                labels.add(first.getValue() != null ? first.getValue() : first.getKey());
            }
        }
        return labels;
    }

    private float textWidth(PDFont font, String text, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    private void drawHorizontalLine(PDPageContentStream cs, float x, float y, float width)
            throws IOException {
        cs.setLineWidth(1f);
        cs.setStrokingColor(Color.GRAY);
        cs.moveTo(x, y);
        cs.lineTo(x + width, y);
        cs.stroke();
        cs.setStrokingColor(Color.BLACK);
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() > maxLength) {
            return text.substring(0, maxLength - 3) + "...";
        }
        return text;
    }
}
