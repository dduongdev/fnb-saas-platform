package com.project.fnb.modules.pos.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO cho hóa đơn (Invoice).
 * 
 * <p><b>Purpose:</b> Sử dụng để tạo PDF invoice hoặc hiển thị thông tin hóa đơn.
 * Chứa đầy đủ thông tin quán, order, chi tiết món, thanh toán.</p>
 * 
 * <p><b>Fields:</b></p>
 * <ul>
 *   <li><b>Thông tin quán:</b> tenantName, tenantAddress, tenantLogo</li>
 *   <li><b>Thông tin order:</b> orderId, tableName, checkInTime, checkOutTime, cashierName</li>
 *   <li><b>Chi tiết:</b> items (List&lt;InvoiceItemDto&gt;), totalAmount, paymentMethod</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Print hóa đơn sau khi thanh toán</li>
 *   <li>Export PDF invoice</li>
 *   <li>Gửi email hóa đơn cho khách</li>
 * </ul>
 * 
 * @see com.project.fnb.modules.pos.entity.Order
 */
@Data
@Builder
public class InvoiceDto {
    private String tenantName;
    private String tenantAddress;
    private String tenantLogo;

    private Long orderId;
    private String tableName;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;

    private List<InvoiceItemDto> items;
    private BigDecimal totalAmount;
    private String paymentMethod;

    /**
     * DTO cho mỗi món trong hóa đơn.
     */
    @Data
    @Builder
    public static class InvoiceItemDto {
        private String productName;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal total;
    }
}