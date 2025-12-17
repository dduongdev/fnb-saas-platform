package com.project.fnb.modules.pos.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InvoiceDto {
    // Thông tin quán
    private String tenantName;
    private String tenantAddress;
    private String tenantLogo;

    // Thông tin đơn
    private Long orderId;
    private String tableName;
    private LocalDateTime checkInTime; // order.createdAt
    private LocalDateTime checkOutTime; // now
    private String cashierName; // Tên nhân viên thu ngân

    // Chi tiết
    private List<InvoiceItemDto> items;
    private BigDecimal totalAmount;
    private String paymentMethod;

    @Data
    @Builder
    public static class InvoiceItemDto {
        private String productName;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal total;
    }
}