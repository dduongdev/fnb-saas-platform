package com.project.fnb.modules.menu.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private String description;
    private String status;
    private Integer categoryId;
    private String categoryName;
    private List<ImageDto> images;

    @Data
    @Builder
    public static class ImageDto {
        private Long id;
        private String url;
        private Boolean isPrimary;
    }
}