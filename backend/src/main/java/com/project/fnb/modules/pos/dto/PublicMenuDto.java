package com.project.fnb.modules.pos.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO cho menu công khai (khách truy cập qua QR code).
 * 
 * <p><b>Purpose:</b> Hiển thị menu cho khách scan QR bàn, không cần authentication.
 * Chỉ hiển thị sản phẩm AVAILABLE và thông tin cơ bản.</p>
 * 
 * <p><b>Grouping:</b> Sản phẩm được nhóm theo danh mục (Category) để dễ navigate.</p>
 * 
 * <p><b>Fields:</b></p>
 * <ul>
 *   <li>categoryId: ID danh mục</li>
 *   <li>categoryName: Tên danh mục (VD: "Món chính", "Nước uống")</li>
 *   <li>products: Danh sách sản phẩm thuộc danh mục này</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Khách scan QR bàn để xem menu</li>
 *   <li>API endpoint: {@code GET /api/pos/customer/menu?tableId={tableId}}</li>
 *   <li>Không cần authentication (public API)</li>
 * </ul>
 * 
 * @see com.project.fnb.modules.pos.controller.CustomerController
 */
@Data
@Builder
public class PublicMenuDto {
    private Integer categoryId;
    private String categoryName;
    private List<ProductItem> products;

    /**
     * DTO cho mỗi sản phẩm trong menu công khai.
     */
    @Data
    @Builder
    public static class ProductItem {
        private Long id;
        private String name;
        private BigDecimal price;
        private String description;
        private String thumbnailUrl; 
        private String status;
    }
}