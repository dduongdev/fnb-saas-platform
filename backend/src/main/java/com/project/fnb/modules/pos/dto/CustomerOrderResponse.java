package com.project.fnb.modules.pos.dto;

import com.project.fnb.modules.pos.entity.ServingSession;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO cho customer order (public API) - Đơn giản hơn SessionResponse.
 * 
 * <p><b>Purpose:</b> Response cho khách sau khi order qua QR code.
 * Chỉ chứa thông tin cần thiết cho khách, không expose thông tin nội bộ.</p>
 * 
 * <p><b>Fields:</b></p>
 * <ul>
 *   <li>sessionId: ID để track trạng thái order</li>
 *   <li>status: Trạng thái session (PENDING, ACTIVE, COMPLETED, CANCELLED)</li>
 *   <li>statusMessage: Message thân thiện với khách (VD: "Đang chờ nhân viên xác nhận...")</li>
 *   <li>tableId, tableName: Thông tin bàn</li>
 *   <li>items: Danh sách món đã order</li>
 *   <li>totalAmount: Tổng tiền</li>
 *   <li>rejectReason: Lý do từ chối (nếu status = CANCELLED)</li>
 * </ul>
 * 
 * <p><b>Status Messages:</b></p>
 * <ul>
 *   <li>PENDING: "Đang chờ nhân viên xác nhận..."</li>
 *   <li>ACTIVE: "Order đã được xác nhận. Món đang được chuẩn bị."</li>
 *   <li>COMPLETED: "Đã thanh toán. Cảm ơn quý khách!"</li>
 *   <li>CANCELLED: "Order đã bị hủy."</li>
 * </ul>
 * 
 * @see com.project.fnb.modules.pos.controller.CustomerController
 * @see CustomerOrderRequest
 */
@Data
@Builder
public class CustomerOrderResponse {

    private Long sessionId;
    private ServingSession.SessionStatus status;
    private String statusMessage;
    private String tableId;
    private String tableName;
    private LocalDateTime createdAt;
    private List<OrderItemDto> items;
    private BigDecimal totalAmount;
    private String rejectReason; // Lý do từ chối (nếu bị reject)

    @Data
    @Builder
    public static class OrderItemDto {
        private Long id;
        private String productName;
        private String productImage;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal total;
        private String note;
        private String status;
    }

    /**
     * Tạo message mô tả trạng thái session theo ngôn ngữ thân thiện với khách.
     * 
     * <p><b>Use Case:</b> Hiển thị trong UI khách để khách hiểu trạng thái order hiện tại.</p>
     * 
     * @param status Trạng thái session
     * @return Message thân thiện với khách
     */
    public static String getStatusMessage(ServingSession.SessionStatus status) {
        return switch (status) {
            case PENDING -> "Đang chờ nhân viên xác nhận...";
            case ACTIVE -> "Order đã được xác nhận. Món đang được chuẩn bị.";
            case COMPLETED -> "Đã thanh toán. Cảm ơn quý khách!";
            case CANCELLED -> "Order đã bị hủy.";
        };
    }
}
