package com.project.fnb.modules.menu.controller;

import com.project.fnb.aspect.OwnerPermissionValidator;
import com.project.fnb.aspect.RequirePermission;
import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.menu.dto.CategoryDto;
import com.project.fnb.modules.menu.entity.Category;
import com.project.fnb.modules.menu.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryDto>> getCategories() {
        List<Category> categories = categoryService.getCategories();
        List<CategoryDto> dtos = categories.stream().map(c -> CategoryDto.builder()
                .id(c.getId())
                .name(c.getName())
                .displayOrder(c.getDisplayOrder())
                .isDefault(c.getIsDefault())
                .build()).collect(Collectors.toList());
        return ApiResponse.success(dtos);
    }

    @PostMapping
    @RequirePermission(OwnerPermissionValidator.class)
    public ApiResponse<CategoryDto> create(@RequestParam String name, 
                                           @RequestParam(defaultValue = "1") Integer order) {
        Category c = categoryService.createCategory(name, order);
        return ApiResponse.success(CategoryDto.builder()
                .id(c.getId()).name(c.getName()).displayOrder(c.getDisplayOrder()).isDefault(c.getIsDefault())
                .build());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(OwnerPermissionValidator.class)
    public ApiResponse<String> delete(@PathVariable Integer id) {
        categoryService.deleteCategory(id);
        return ApiResponse.success("Đã xóa danh mục thành công");
    }
}