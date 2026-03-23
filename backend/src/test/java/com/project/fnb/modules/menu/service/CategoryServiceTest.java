package com.project.fnb.modules.menu.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.menu.entity.Category;
import com.project.fnb.modules.menu.repository.CategoryRepository;
import com.project.fnb.modules.menu.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    private static final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createDefaultCategory_ShouldSaveCategory() {
        categoryService.createDefaultCategory(TENANT_ID);

        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void createCategory_ShouldSaveAndReturnCategory() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        Category result = categoryService.createCategory("Food", 1);

        assertNotNull(result);
        assertEquals("Food", result.getName());
        assertEquals(1, result.getDisplayOrder());
    }

    @Test
    void getCategories_ShouldReturnList() {
        List<Category> categories = new ArrayList<>();
        categories.add(new Category());
        when(categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc()).thenReturn(categories);

        List<Category> result = categoryService.getCategories();

        assertEquals(1, result.size());
        verify(categoryRepository).findByIsActiveTrueOrderByDisplayOrderAsc();
    }
    
    @Test
    void updateCategory_ShouldUpdateFields() {
        Category cat = new Category();
        cat.setId(1);
        cat.setName("Old Name");
        cat.setDisplayOrder(1);
        cat.setIsDefault(false);
        
        when(categoryRepository.findById(1)).thenReturn(Optional.of(cat));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
        
        Category result = categoryService.updateCategory(1, "New Name", 2);
        
        assertEquals("New Name", result.getName());
        assertEquals(2, result.getDisplayOrder());
        verify(categoryRepository).save(cat);
    }
    
    @Test
    void updateCategory_WhenDefaultCategory_ShouldThrowAppException() {
        Category cat = new Category();
        cat.setId(1);
        cat.setIsDefault(true);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(cat));
        
        AppException ex = assertThrows(AppException.class, () -> {
            categoryService.updateCategory(1, "New Name", 2);
        });
        assertEquals(400, ex.getErrorCode());
    }

    @Test
    void deleteCategory_WhenCategoryNotExists_ShouldThrowAppException() {
        when(categoryRepository.findById(1)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> {
            categoryService.deleteCategory(1);
        });

        assertEquals(404, ex.getErrorCode());
    }

    @Test
    void deleteCategory_WhenDefaultCategory_ShouldThrowAppException() {
        Category category = new Category();
        category.setIsDefault(true);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));

        AppException ex = assertThrows(AppException.class, () -> {
            categoryService.deleteCategory(1);
        });

        assertEquals(400, ex.getErrorCode());
    }

    @Test
    void deleteCategory_ShouldMoveProductsAndDelete() {
        Category category = new Category();
        category.setId(1);
        category.setIsDefault(false);
        
        Category defaultCategory = new Category();
        defaultCategory.setId(99);
        defaultCategory.setIsDefault(true);
        
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIsDefaultTrue()).thenReturn(Optional.of(defaultCategory));

        categoryService.deleteCategory(1);

        verify(productRepository).moveProductsToCategory(1, 99);
        verify(categoryRepository).delete(category);
    }
}
