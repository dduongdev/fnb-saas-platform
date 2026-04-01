package com.project.fnb.modules.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class TopProductDto {
    private Long productId;
    private String productName;
    private Long quantitySold;
    private BigDecimal totalRevenue;
    private Long quantityCancelled;
    private BigDecimal cancelledRevenue;
}