package com.project.fnb.modules.menu.controller;

import com.project.fnb.aspect.OwnerPermissionValidator;
import com.project.fnb.aspect.RequirePermission;
import com.project.fnb.aspect.WaiterPermissionValidator;
import com.project.fnb.common.dto.ApiResponse;
import com.project.fnb.modules.menu.dto.ProductResponse;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.menu.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Lấy danh sách sản phẩm theo tenant hiện tại.
     * Hỗ trợ filter theo categoryId và status.
     */
    @GetMapping
    @RequirePermission({OwnerPermissionValidator.class, WaiterPermissionValidator.class})
    public ApiResponse<Page<ProductResponse>> getProducts(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Product.ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return ApiResponse.success(productService.getProducts(categoryId, status, pageable));
    }

    @GetMapping("/{id}")
    @RequirePermission({OwnerPermissionValidator.class, WaiterPermissionValidator.class})
    public ApiResponse<ProductResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.success(productService.getProductDetail(id));
    }

    @PostMapping(consumes = { "multipart/form-data" })
    @RequirePermission(OwnerPermissionValidator.class)
    public ApiResponse<ProductResponse> create(
            @RequestParam Integer categoryId,
            @RequestParam String name,
            @RequestParam BigDecimal price,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) List<MultipartFile> images
    ) {
        return ApiResponse.success(productService.createProduct(categoryId, name, price, description, images));
    }

    @PutMapping("/{id}")
    @RequirePermission(OwnerPermissionValidator.class)
    public ApiResponse<ProductResponse> updateInfo(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Product.ProductStatus status
    ) {
        return ApiResponse.success(productService.updateProductInfo(id, name, price, description, categoryId, status));
    }

    @PostMapping(value = "/{id}/images", consumes = { "multipart/form-data" })
    @RequirePermission(OwnerPermissionValidator.class)
    public ApiResponse<String> addImages(@PathVariable Long id, @RequestParam List<MultipartFile> images) {
        productService.addImages(id, images);
        return ApiResponse.success("Đã thêm ảnh thành công");
    }

    @DeleteMapping("/images/{imageId}")
    @RequirePermission(OwnerPermissionValidator.class)
    public ApiResponse<String> deleteImage(@PathVariable Long imageId) {
        productService.removeImage(imageId);
        return ApiResponse.success("Đã xóa ảnh");
    }

    @DeleteMapping("/{id}")
    @RequirePermission(OwnerPermissionValidator.class)
    public ApiResponse<String> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ApiResponse.success("Đã xóa sản phẩm");
    }
}