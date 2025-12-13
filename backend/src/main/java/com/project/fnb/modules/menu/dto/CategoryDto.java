package com.project.fnb.modules.menu.dto;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryDto {
    private Integer id;
    private String name;
    private Integer displayOrder;
    private Boolean isDefault;
}