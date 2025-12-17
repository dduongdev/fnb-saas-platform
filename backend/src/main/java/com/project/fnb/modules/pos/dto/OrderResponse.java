package com.project.fnb.modules.pos.dto;

import com.project.fnb.modules.menu.entity.ProductImage; // Import Entity này
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.entity.OrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private Integer tableId;
    private String tableName;
    private BigDecimal totalAmount;
    private Order.OrderStatus status;
    private List<ItemDto> items;

    @Data
    @Builder
    public static class ItemDto {
        private Long id;
        private Long productId;
        private String productName;
        private String productImage; // URL ảnh thumbnail
        private Integer quantity;
        private BigDecimal price;
        private String note;
        private OrderItem.ItemStatus status;
        private BigDecimal total;
    }
    
    public static OrderResponse fromEntity(Order order) {
        List<ItemDto> itemDtos = order.getItems().stream()
                // Sắp xếp item mới nhất lên đầu (Optional)
                .sorted(Comparator.comparing(OrderItem::getCreatedAt).reversed())
                .map(i -> {
                    // [LOGIC LẤY ẢNH]
                    String imageUrl = null;
                    if (i.getProduct().getImages() != null && !i.getProduct().getImages().isEmpty()) {
                        imageUrl = i.getProduct().getImages().stream()
                                .filter(ProductImage::getIsPrimary) // Ưu tiên ảnh chính
                                .findFirst()
                                .map(ProductImage::getImageUrl)
                                // Nếu không có ảnh chính, lấy ảnh đầu tiên
                                .orElse(i.getProduct().getImages().get(0).getImageUrl());
                    }

                    return ItemDto.builder()
                            .id(i.getId())
                            .productId(i.getProduct().getId())
                            .productName(i.getProduct().getName())
                            .productImage(imageUrl) // Gán URL vào DTO
                            .quantity(i.getQuantity())
                            .price(i.getPrice())
                            .note(i.getNote())
                            .status(i.getStatus())
                            .total(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                            .build();
                })
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .tableId(order.getTable().getId())
                .tableName(order.getTable().getName())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .items(itemDtos)
                .build();
    }
}