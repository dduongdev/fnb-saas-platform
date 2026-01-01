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
 * DTO response cho Session API.
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
        private Integer id;
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
