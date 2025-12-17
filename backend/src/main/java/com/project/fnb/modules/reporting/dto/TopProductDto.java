package com.project.fnb.modules.reporting.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class TopProductDto {
    private String productName;
    private Long quantitySold;
    private BigDecimal totalRevenue;
}