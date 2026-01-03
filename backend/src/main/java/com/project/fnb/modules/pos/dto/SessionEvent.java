package com.project.fnb.modules.pos.dto;

import com.project.fnb.modules.pos.entity.OrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WebSocket Event DTO cho real-time session updates.
 * 
 * <p><b>Purpose:</b> DTO này được push qua WebSocket mỗi khi có thay đổi trong session
 * (thêm món, xóa món, serve món, update món) để cập nhật UI realtime.</p>
 * 
 * <p><b>WebSocket Topics:</b></p>
 * <ul>
 *   <li>{@code /topic/tenant/{tenantId}/table/{tableId}} - Updates cho từng bàn</li>
 *   <li>{@code /topic/tenant/{tenantId}/sessions} - Updates cho danh sách sessions</li>
 * </ul>
 * 
 * <p><b>Event Types:</b></p>
 * <ul>
 *   <li><b>ORDER_ITEM_ADDED</b> - Khi thêm món mới vào order</li>
 *   <li><b>ORDER_ITEM_DELETED</b> - Khi xóa món (chỉ món PENDING)</li>
 *   <li><b>ORDER_ITEM_SERVED</b> - Khi đánh dấu món đã mang ra khách</li>
 *   <li><b>ORDER_ITEM_UPDATED</b> - Khi cập nhật số lượng món</li>
 *   <li><b>SESSION_UPDATED</b> - Khi session thay đổi state (full state sync)</li>
 * </ul>
 * 
 * <p><b>Fields:</b></p>
 * <ul>
 *   <li>type: Loại event (ORDER_ITEM_ADDED, ORDER_ITEM_DELETED, etc.)</li>
 *   <li>sessionId: ID của session bị thay đổi</li>
 *   <li>item: Thông tin OrderItem đã thay đổi (OrderItemEvent)</li>
 *   <li>newTotalAmount: Tổng tiền mới của session sau khi thay đổi</li>
 *   <li>sessionData: Full SessionResponse để tránh race condition (optional)</li>
 *   <li>timestamp: Thời điểm event xảy ra</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Cập nhật UI POS realtime khi có thay đổi</li>
 *   <li>Sync trạng thái giữa nhiều devices (tablet kitchen, staff phones)</li>
 *   <li>Hiển thị thông báo cho khách qua QR ordering UI</li>
 * </ul>
 * 
 * @see com.project.fnb.modules.pos.service.SessionService
 * @see com.project.fnb.modules.pos.entity.OrderItem
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
     * Tạo event từ OrderItem khi có thay đổi (add/update/serve).
     * 
     * <p><b>Event Creation:</b> Method này convert OrderItem entity sang OrderItemEvent DTO
     * và wrap trong SessionEvent với type, sessionId, totalAmount.</p>
     * 
     * <p><b>Product Image:</b> Tự động lấy ảnh chính (isPrimary) của product, fallback về ảnh đầu tiên.</p>
     * 
     * @param type Event type (ORDER_ITEM_ADDED, ORDER_ITEM_UPDATED, ORDER_ITEM_SERVED)
     * @param sessionId ID của session chứa item này
     * @param item OrderItem entity đã thay đổi
     * @param sessionTotal Tổng tiền mới của session sau khi thay đổi
     * @return SessionEvent ready để push qua WebSocket
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
     * Tạo event khi xóa món (ORDER_ITEM_DELETED).
     * 
     * <p><b>Lightweight Event:</b> Khi xóa món, chỉ cần gửi itemId để UI remove khỏi danh sách.
     * Không cần gửi full item data.</p>
     * 
     * @param sessionId ID của session chứa item bị xóa
     * @param itemId ID của OrderItem bị xóa
     * @param sessionTotal Tổng tiền mới của session sau khi xóa món
     * @return SessionEvent với type=ORDER_ITEM_DELETED
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
