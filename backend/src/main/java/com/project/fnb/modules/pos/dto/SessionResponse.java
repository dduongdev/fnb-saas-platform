package com.project.fnb.modules.pos.dto;

import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.entity.ServingSession;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response DTO cho Session API - Chứa thông tin đầy đủ của một session.
 * 
 * <p><b>Purpose:</b> DTO này được sử dụng để trả về thông tin session cho staff POS UI,
 * bao gồm danh sách bàn, orders, items, và tổng tiền.</p>
 * 
 * <p><b>Fields:</b></p>
 * <ul>
 *   <li>sessionId: ID duy nhất của session</li>
 *   <li>status: Trạng thái (PENDING, ACTIVE, COMPLETED, CANCELLED)</li>
 *   <li>startedAt, endedAt: Thời gian bắt đầu và kết thúc session</li>
 *   <li>guestCount: Số khách</li>
 *   <li>note: Ghi chú session</li>
 *   <li>tables: Danh sách bàn trong session (có thể nhiều bàn merged)</li>
 *   <li>orders: Danh sách orders thuộc session, mỗi order gắn với 1 bàn</li>
 *   <li>totalAmount: Tổng tiền của tất cả orders trong session</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Response của API {@code GET /api/pos/sessions/{id}}</li>
 *   <li>WebSocket updates qua topic {@code /topic/tenant/{tenantId}/sessions}</li>
 *   <li>Hiển thị chi tiết session trong POS UI</li>
 * </ul>
 * 
 * @see com.project.fnb.modules.pos.entity.ServingSession
 * @see com.project.fnb.modules.pos.service.SessionService
 */
@Data
@Builder
public class SessionResponse {
    private Long sessionId;
    private ServingSession.SessionStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer guestCount;
    private String note;
    private List<SessionTableDto> tables;
    private List<SessionOrderDto> orders;
    private BigDecimal totalAmount;

    @Data
    @Builder
    public static class SessionTableDto {
        private String id;
        private String name;
        private DiningTable.Status status;
    }

    @Data
    @Builder
    public static class SessionOrderDto {
        private Long orderId;
        private Order.OrderStatus status;
        private BigDecimal totalAmount;
        private List<OrderResponse.ItemDto> items;
    }

    /**
     * Convert từ Entity sang DTO.
     * 
     * <p><b>Conversion Logic:</b></p>
     * <ul>
     *   <li>tables: Map từ session.getTables() sang SessionTableDto</li>
     *   <li>orders: Map từ session.getOrders() sang SessionOrderDto, mỗi order chứa items</li>
     *   <li>items: Sắp xếp theo createdAt giảm dần (mới nhất lên đầu)</li>
     *   <li>totalAmount: Tổng của tất cả order.totalAmount</li>
     * </ul>
     * 
     * @param session ServingSession entity cần convert
     * @return SessionResponse DTO
     */
    public static SessionResponse fromEntity(ServingSession session) {
        List<SessionTableDto> tableDtos = session.getTables().stream()
                .map(t -> SessionTableDto.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .status(t.getStatus())
                        .build())
                .collect(Collectors.toList());

        List<SessionOrderDto> orderDtos = session.getOrders().stream()
                .map(o -> SessionOrderDto.builder()
                        .orderId(o.getId())
                        .status(o.getStatus())
                        .totalAmount(o.getTotalAmount())
                        .items(o.getItems().stream()
                                .sorted(java.util.Comparator.comparing(com.project.fnb.modules.pos.entity.OrderItem::getCreatedAt).reversed())
                                .map(i -> OrderResponse.ItemDto.builder()
                                        .id(i.getId())
                                        .productId(i.getProduct().getId())
                                        .productName(i.getProduct().getName())
                                        .productImage(getProductImage(i))
                                        .quantity(i.getQuantity())
                                        .price(i.getPrice())
                                        .note(i.getNote())
                                        .status(i.getStatus())
                                        .total(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                                        .createdAt(i.getCreatedAt())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        BigDecimal total = session.getOrders().stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SessionResponse.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .guestCount(session.getGuestCount())
                .note(session.getNote())
                .tables(tableDtos)
                .orders(orderDtos)
                .totalAmount(total)
                .build();
    }

    private static String getProductImage(com.project.fnb.modules.pos.entity.OrderItem item) {
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
