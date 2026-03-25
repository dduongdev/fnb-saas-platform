package com.project.fnb.modules.reporting.controller;

import com.project.fnb.aspect.OwnerPermissionValidator;
import com.project.fnb.aspect.RequirePermission;
import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.reporting.dto.HourlyStatDto;
import com.project.fnb.modules.reporting.dto.RevenueReportDto;
import com.project.fnb.modules.reporting.dto.TopProductDto;
import com.project.fnb.modules.reporting.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@RequirePermission(OwnerPermissionValidator.class) 
public class ReportingController {

    private final ReportingService reportingService;

    // 1. Biểu đồ doanh thu
    // GET /api/reports/revenue?from=2023-01-01&to=2023-01-31
    @GetMapping("/revenue")
    public ApiResponse<List<RevenueReportDto>> getRevenueReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        // Mặc định: 30 ngày gần nhất
        if (to == null) to = LocalDate.now();
        if (from == null) from = to.minusDays(30);

        return ApiResponse.success(reportingService.getRevenueReport(from, to));
    }

    // 2. Top món bán chạy
    // GET /api/reports/top-products?limit=5
    @GetMapping("/top-products")
    public ApiResponse<List<TopProductDto>> getTopProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "5") int limit
    ) {
        if (to == null) to = LocalDate.now();
        if (from == null) from = to.minusDays(30);

        return ApiResponse.success(reportingService.getTopSelling(from, to, limit));
    }

    // 3. Biểu đồ nhiệt (Khung giờ đắt khách)
    // GET /api/reports/peak-hours
    @GetMapping("/peak-hours")
    public ApiResponse<List<HourlyStatDto>> getPeakHours(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (to == null) to = LocalDate.now();
        if (from == null) from = to.minusDays(30); // Phân tích xu hướng trong 1 tháng

        return ApiResponse.success(reportingService.getPeakHours(from, to));
    }
}