package com.project.fnb.modules.pos.dto;

import com.project.fnb.modules.menu.entity.ProductImage;
import com.project.fnb.modules.pos.entity.DiningTable;
import com.project.fnb.modules.pos.entity.Order;
import com.project.fnb.modules.pos.entity.OrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response DTO cho Order - Được sử dụng cho WebSocket updates.
 * 
 * <p><b>Purpose:</b> DTO này được push qua WebSocket mỗi khi order có thay đổi
 * (thêm món, xóa món, serve món, etc.) để cập nhật UI realtime.</p>
 * 
 * <p><b>WebSocket Topics:</b></p>
 * <ul>
 *   <li>{@code /topic/tenant/{tenantId}/table/{tableId}} - Update cho 1 bàn cụ thể</li>
 *   <li>{@code /topic/tenant/{tenantId}/sessions} - Update cho danh sách sessions</li>
 * </ul>
 * 
 * <p><b>Fields:</b></p>
 * <ul>
 *   <li>id: Order ID</li>
 *   <li>tableId, tableName: Bàn gắn với order (từ session.getPrimaryTable() hoặc order.table legacy)</li>
 *   <li>totalAmount: Tổng tiền order</li>
 *   <li>status: Trạng thái order (ACTIVE, PAID, CANCELLED)</li>
 *   <li>items: Danh sách món, sắp xếp theo createdAt giảm dần</li>
 * </ul>
 * 
 * @see com.project.fnb.modules.pos.entity.Order
 */
@Data
@Builder
public class OrderResponse {
    private Long id;
    private String tableId;
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
        private LocalDateTime createdAt; // Thời gian thêm món
    }
    
    /**
     * Convert Order entity sang OrderResponse DTO.
     * 
     * <p><b>Image Logic:</b> Lấy ảnh chính (isPrimary=true) của product,
     * nếu không có thì lấy ảnh đầu tiên.</p>
     * 
     * <p><b>Table Logic:</b> Sử dụng {@code order.getPrimaryTable()} để lấy bàn chính
     * từ session, fallback về order.table (legacy) nếu không có session.</p>
     * 
     * <p><b>Item Sorting:</b> Items được sắp xếp theo createdAt giảm dần
     * (đặt mới nhất lên đầu).</p>
     * 
     * @param order Order entity cần convert
     * @return OrderResponse DTO
     */
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
                                .orElseGet(() -> i.getProduct().getImages().stream()
                                        .findFirst()
                                        .map(ProductImage::getImageUrl)
                                        .orElse(null));
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
                            .createdAt(i.getCreatedAt()) // Thời gian thêm món
                            .build();
                })
                .collect(Collectors.toList());

        // Sử dụng getPrimaryTable() để lấy bàn từ session hoặc fallback về table cũ
        DiningTable table = order.getPrimaryTable();

        return OrderResponse.builder()
                .id(order.getId())
                .tableId(table != null ? table.getId() : null)
                .tableName(table != null ? table.getName() : "N/A")
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .items(itemDtos)
                .build();
    }
}