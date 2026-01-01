package com.project.fnb.modules.pos.dto;

import com.project.fnb.modules.pos.entity.OrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO cho WebSocket events liên quan đến Session.
 * 
 * <p>Event types:</p>
 * <ul>
 *   <li>ORDER_ITEM_ADDED - Khi thêm món mới</li>
 *   <li>ORDER_ITEM_DELETED - Khi xóa món (chỉ PENDING)</li>
 *   <li>ORDER_ITEM_SERVED - Khi đánh dấu món đã mang ra</li>
 *   <li>ORDER_ITEM_UPDATED - Khi cập nhật số lượng</li>
 *   <li>SESSION_UPDATED - Khi session thay đổi (full state)</li>
 * </ul>
 */
@Data
@Builder
public class SessionEvent {
    
    private String type; // ORDER_ITEM_ADDED, ORDER_ITEM_DELETED, ORDER_ITEM_SERVED, etc.
    private Long sessionId;
    private OrderItemEvent item; // Thông tin item đã thay đổi
    private BigDecimal newTotalAmount; // Tổng tiền mới của session
    private Object sessionData; // Toàn bộ session data (SessionResponse) để tránh race condition
    private LocalDateTime timestamp;

    @Data
    @Builder
    public static class OrderItemEvent {
        private Long id;
        private Long productId;
        private String productName;
        private String productImage;
        private Integer quantity;
        private BigDecimal price;
        private String note;
        private OrderItem.ItemStatus status;
        private BigDecimal total;
    }

    /**
     * Tạo event từ OrderItem.
     */
    public static SessionEvent createItemEvent(String type, Long sessionId, OrderItem item, BigDecimal sessionTotal) {
        OrderItemEvent itemEvent = OrderItemEvent.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productImage(getProductImage(item))
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .note(item.getNote())
                .status(item.getStatus())
                .total(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build();

        return SessionEvent.builder()
                .type(type)
                .sessionId(sessionId)
                .item(itemEvent)
                .newTotalAmount(sessionTotal)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Tạo event xóa item (chỉ cần ID).
     */
    public static SessionEvent createDeleteEvent(Long sessionId, Long itemId, BigDecimal sessionTotal) {
        OrderItemEvent itemEvent = OrderItemEvent.builder()
                .id(itemId)
                .build();

        return SessionEvent.builder()
                .type("ORDER_ITEM_DELETED")
                .sessionId(sessionId)
                .item(itemEvent)
                .newTotalAmount(sessionTotal)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static String getProductImage(OrderItem item) {
        if (item.getProduct().getImages() == null || item.getProduct().getImages().isEmpty()) {
            return null;
        }
        return item.getProduct().getImages().stream()
                .filter(img -> img.getIsPrimary())
                .findFirst()
                .map(img -> img.getImageUrl())
                .orElse(item.getProduct().getImages().get(0).getImageUrl());
    }
}
