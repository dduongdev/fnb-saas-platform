package com.project.fnb.modules.menu.service;

import com.project.fnb.modules.menu.entity.Category;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.menu.entity.ProductImage;
import com.project.fnb.modules.menu.repository.CategoryRepository;
import com.project.fnb.modules.pos.dto.PublicMenuDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private MenuService menuService;

    @Test
    void getPublicMenu_ShouldReturnFilteredCategoriesAndProducts() {
        // Arrange
        Category category = new Category();
        category.setId(1);
        category.setName("Category 1");

        Product activeProduct = new Product();
        activeProduct.setId(1L);
        activeProduct.setName("Active Product");
        activeProduct.setPrice(new BigDecimal("100"));
        activeProduct.setStatus(Product.ProductStatus.AVAILABLE);
        activeProduct.setIsDeleted(false);
        
        ProductImage image1 = new ProductImage();
        image1.setIsPrimary(true);
        image1.setImageUrl("http://image1.com");
        activeProduct.setImages(Collections.singletonList(image1));

        Product hiddenProduct = new Product();
        hiddenProduct.setId(2L);
        hiddenProduct.setName("Hidden Product");
        hiddenProduct.setStatus(Product.ProductStatus.HIDDEN);
        hiddenProduct.setIsDeleted(false);

        Product deletedProduct = new Product();
        deletedProduct.setId(3L);
        deletedProduct.setName("Deleted Product");
        deletedProduct.setStatus(Product.ProductStatus.AVAILABLE);
        deletedProduct.setIsDeleted(true);

        category.setProducts(Arrays.asList(activeProduct, hiddenProduct, deletedProduct));

        Category emptyCategory = new Category();
        emptyCategory.setId(2);
        emptyCategory.setName("Empty Category");
        emptyCategory.setProducts(new ArrayList<>());

        // Mock the new method that includes product images (N+1 fix)
        when(categoryRepository.findAllWithProductsAndImages()).thenReturn(Arrays.asList(category, emptyCategory));

        // Act
        List<PublicMenuDto> result = menuService.getPublicMenu();

        // Assert
        assertEquals(1, result.size()); // emptyCategory should be filtered out
        
        PublicMenuDto resultCategory = result.get(0);
        assertEquals(1, resultCategory.getCategoryId());
        assertEquals("Category 1", resultCategory.getCategoryName());
        
        List<PublicMenuDto.ProductItem> resultProducts = resultCategory.getProducts();
        assertEquals(1, resultProducts.size()); // only activeProduct should remain
        
        PublicMenuDto.ProductItem resultProduct = resultProducts.get(0);
        assertEquals(1L, resultProduct.getId());
        assertEquals("Active Product", resultProduct.getName());
        assertEquals("AVAILABLE", resultProduct.getStatus());
        assertEquals("http://image1.com", resultProduct.getThumbnailUrl());
    }
}