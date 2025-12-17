package com.project.fnb.modules.reporting.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class RevenueReportDto {
    private LocalDate date;
    private BigDecimal revenue;
    private Integer totalOrders;
}