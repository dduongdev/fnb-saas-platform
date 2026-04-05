package com.project.fnb.modules.menu.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.fnb.modules.menu.entity.Category;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.menu.entity.ProductImage;
import com.project.fnb.modules.menu.repository.CategoryRepository;
import com.project.fnb.modules.pos.dto.PublicMenuDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<PublicMenuDto> getPublicMenu() {
        // Fix N+1 query: Use findAllWithProductsAndImages() with complete JOIN FETCH
        // Before: 1 query + N queries (products) + M queries (images) = 1 + N + M
        // After: 1 query with JOIN FETCH product.images = 1 query
        List<Category> categories = categoryRepository.findAllWithProductsAndImages();

        return categories.stream().map(cat -> PublicMenuDto.builder()
                .categoryId(cat.getId())
                .categoryName(cat.getName())
                .products(cat.getProducts().stream() 
                        .filter(p -> p.getStatus() != Product.ProductStatus.HIDDEN && !p.getIsDeleted())
                        .map(p -> {
                            String thumb = null;
                            if (p.getImages() != null && !p.getImages().isEmpty()) {
                                thumb = p.getImages().stream()
                                        .filter(ProductImage::getIsPrimary).findFirst()
                                        .map(ProductImage::getImageUrl)
                                        .orElseGet(() -> p.getImages().stream()
                                                .findFirst()
                                                .map(ProductImage::getImageUrl)
                                                .orElse(null));
                            }

                            return PublicMenuDto.ProductItem.builder()
                                    .id(p.getId())
                                    .name(p.getName())
                                    .price(p.getPrice())
                                    .description(p.getDescription())
                                    .status(p.getStatus().name())
                                    .thumbnailUrl(thumb)
                                    .build();
                        })
                        .toList())
                .build())
                .filter(dto -> !dto.getProducts().isEmpty())
                .toList();
    }
}
