package com.project.fnb.modules.menu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.fnb.modules.menu.dto.CategoryDto;
import com.project.fnb.modules.menu.entity.Category;
import com.project.fnb.modules.menu.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private CategoryController categoryController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(categoryController).build();
    }

    @Test
    void getCategories_ShouldReturnList() throws Exception {
        Category cat = new Category();
        cat.setId(1);
        cat.setName("Test Category");
        cat.setDisplayOrder(1);
        cat.setIsDefault(false);

        when(categoryService.getCategories()).thenReturn(Arrays.asList(cat));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Test Category"));
    }

    @Test
    void create_ShouldReturnNewCategory() throws Exception {
        Category cat = new Category();
        cat.setId(1);
        cat.setName("New Category");
        cat.setDisplayOrder(2);
        cat.setIsDefault(false);

        when(categoryService.createCategory(anyString(), anyInt())).thenReturn(cat);

        mockMvc.perform(post("/api/categories")
                .param("name", "New Category")
                .param("order", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("New Category"));
    }

    @Test
    void delete_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Đã xóa danh mục thành công"));

        verify(categoryService).deleteCategory(1);
    }
}