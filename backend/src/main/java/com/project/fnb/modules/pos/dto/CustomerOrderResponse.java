package com.project.fnb.modules.pos.dto;

import com.project.fnb.modules.pos.entity.ServingSession;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO cho customer order (đơn giản hơn SessionResponse).
 */
@Data
@Builder
public class CustomerOrderResponse {

    private Long sessionId;
    private ServingSession.SessionStatus status;
    private String statusMessage;
    private Integer tableId;
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
     * Tạo message mô tả trạng thái cho khách.
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
