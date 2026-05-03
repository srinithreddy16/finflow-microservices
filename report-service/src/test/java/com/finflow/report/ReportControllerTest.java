package com.finflow.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.report.controller.ReportController;
import com.finflow.report.dto.ReportResponseDto;
import com.finflow.report.exception.GlobalExceptionHandler;
import com.finflow.report.exception.ReportNotFoundException;
import com.finflow.report.model.ReportType;
import com.finflow.report.service.ReportService;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ReportService reportService;

    @Test
    void generateReport_Returns202_WhenValid() throws Exception {
        when(reportService.generateReport(any(), any()))
                .thenReturn(
                        new ReportResponseDto(
                                "rep-001",
                                "acc-001",
                                "Transaction History",
                                "READY",
                                "TRANSACTION_HISTORY",
                                "PDF",
                                "https://download-url",
                                100L,
                                LocalDate.now().minusDays(10),
                                LocalDate.now(),
                                null,
                                "corr-001",
                                Instant.now(),
                                Instant.now(),
                                Instant.now(),
                                Instant.now().plusSeconds(3600)));

        mockMvc.perform(
                        post("/api/reports")
                                .contentType("application/json")
                                .content("{\"accountId\":\"acc-001\",\"reportType\":\"TRANSACTION_HISTORY\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("rep-001"));
    }

    @Test
    void generateReport_Returns400_WhenAccountIdMissing() throws Exception {
        mockMvc.perform(
                        post("/api/reports")
                                .contentType("application/json")
                                .content("{\"reportType\":\"TRANSACTION_HISTORY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void generateReport_Returns400_WhenReportTypeNull() throws Exception {
        mockMvc.perform(
                        post("/api/reports")
                                .contentType("application/json")
                                .content("{\"accountId\":\"acc-001\",\"reportType\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReport_Returns200_WithDownloadUrl() throws Exception {
        when(reportService.getReport("rep-001"))
                .thenReturn(
                        new ReportResponseDto(
                                "rep-001",
                                "acc-001",
                                "Transaction History",
                                "READY",
                                "TRANSACTION_HISTORY",
                                "PDF",
                                "https://download-url",
                                100L,
                                LocalDate.now().minusDays(10),
                                LocalDate.now(),
                                null,
                                "corr-001",
                                Instant.now(),
                                Instant.now(),
                                Instant.now(),
                                Instant.now().plusSeconds(3600)));

        mockMvc.perform(get("/api/reports/rep-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("https://download-url"));
    }

    @Test
    void getReport_Returns404_WhenNotFound() throws Exception {
        when(reportService.getReport(eq("non-existent"))).thenThrow(new ReportNotFoundException("non-existent"));

        mockMvc.perform(get("/api/reports/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("REPORT_NOT_FOUND"));
    }
}
