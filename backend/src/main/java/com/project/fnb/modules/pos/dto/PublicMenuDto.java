package com.project.fnb.modules.pos.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PublicMenuDto {
    private Integer categoryId;
    private String categoryName;
    private List<ProductItem> products;

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