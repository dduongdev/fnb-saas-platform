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
        List<Category> categories = categoryRepository.findAllWithProducts();

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
                                        .orElse(p.getImages().get(0).getImageUrl());
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
