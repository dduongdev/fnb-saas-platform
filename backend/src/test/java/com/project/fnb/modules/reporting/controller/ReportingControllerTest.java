package com.project.fnb.modules.reporting.controller;

import com.project.fnb.modules.reporting.dto.HourlyStatDto;
import com.project.fnb.modules.reporting.dto.RevenueReportDto;
import com.project.fnb.modules.reporting.dto.TopProductDto;
import com.project.fnb.modules.reporting.service.ReportingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReportingService reportingService;

    @InjectMocks
    private ReportingController reportingController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reportingController).build();
    }

    // --- Revenue Report ---

    @Test
    void getRevenueReport_WithDates_ShouldReturnList() throws Exception {
        RevenueReportDto dto = new RevenueReportDto(
                LocalDate.of(2024, 1, 15), BigDecimal.valueOf(500000), 10);

        when(reportingService.getRevenueReport(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/reports/revenue")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].totalRevenue").value(500000))
                .andExpect(jsonPath("$.data[0].orderCount").value(10));

        verify(reportingService).getRevenueReport(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));
    }

    @Test
    void getRevenueReport_DefaultDates_ShouldUse30Days() throws Exception {
        when(reportingService.getRevenueReport(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/reports/revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(reportingService).getRevenueReport(any(LocalDate.class), any(LocalDate.class));
    }

    // --- Top Products ---

    @Test
    void getTopProducts_WithParams_ShouldReturnList() throws Exception {
        TopProductDto dto = new TopProductDto(1L, "Phở Bò", 50L, BigDecimal.valueOf(2500000));

        when(reportingService.getTopSelling(any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/reports/top-products")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("Phở Bò"))
                .andExpect(jsonPath("$.data[0].quantitySold").value(50));

        verify(reportingService).getTopSelling(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31), 5);
    }

    @Test
    void getTopProducts_DefaultParams_ShouldUseDefaults() throws Exception {
        when(reportingService.getTopSelling(any(LocalDate.class), any(LocalDate.class), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/reports/top-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(reportingService).getTopSelling(any(LocalDate.class), any(LocalDate.class), anyInt());
    }

    // --- Peak Hours ---

    @Test
    void getPeakHours_WithDates_ShouldReturnList() throws Exception {
        HourlyStatDto dto = new HourlyStatDto(12, 25L);

        when(reportingService.getPeakHours(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/reports/peak-hours")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].hour").value(12))
                .andExpect(jsonPath("$.data[0].orderCount").value(25));

        verify(reportingService).getPeakHours(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));
    }

    @Test
    void getPeakHours_DefaultDates_ShouldUse30Days() throws Exception {
        when(reportingService.getPeakHours(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/reports/peak-hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(reportingService).getPeakHours(any(LocalDate.class), any(LocalDate.class));
    }
}
