package com.project.fnb.modules.menu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.fnb.modules.menu.dto.ProductResponse;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.menu.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();
    }

    @Test
    void getProducts_ShouldReturnPage() throws Exception {
        ProductResponse response = ProductResponse.builder().id(1L).name("Test Product").build();
        Page<ProductResponse> page = new PageImpl<>(java.util.Arrays.asList(response), PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("id")), 1);

        when(productService.getProducts(eq(null), eq(null), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/products")
                .param("page", "0")
                .param("size", "10"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].name").value("Test Product"));
    }

    @Test
    void getDetail_ShouldReturnProduct() throws Exception {
        ProductResponse response = ProductResponse.builder().id(1L).name("Test Product").build();

        when(productService.getProductDetail(1L)).thenReturn(response);

        mockMvc.perform(get("/api/products/1"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Test Product"));
    }

    @Test
    void createProduct_ShouldReturnNewProduct() throws Exception {
        ProductResponse response = ProductResponse.builder().id(1L).name("New Product").build();

        when(productService.createProduct(eq(1), eq("New Product"), any(BigDecimal.class), eq("Desc"), any()))
                .thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("images", "test.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/api/products")
                .file(file)
                .param("categoryId", "1")
                .param("name", "New Product")
                .param("price", "100.00")
                .param("description", "Desc"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("New Product"));
    }

    @Test
    void updateInfo_ShouldReturnUpdatedProduct() throws Exception {
        ProductResponse response = ProductResponse.builder().id(1L).name("Updated Product").build();

        when(productService.updateProductInfo(eq(1L), eq("Updated Product"), any(BigDecimal.class), eq("Desc"), eq(2), eq(Product.ProductStatus.AVAILABLE)))
                .thenReturn(response);

        mockMvc.perform(put("/api/products/1")
                .param("name", "Updated Product")
                .param("price", "150.00")
                .param("description", "Desc")
                .param("categoryId", "2")
                .param("status", "AVAILABLE"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("Updated Product"));
    }

    @Test
    void addImages_ShouldReturnSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("images", "test.jpg", "image/jpeg", "data".getBytes());

        mockMvc.perform(multipart("/api/products/1/images")
                .file(file))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Đã thêm ảnh thành công"));

        verify(productService).addImages(eq(1L), any());
    }

    @Test
    void deleteImage_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/products/images/1"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Đã xóa ảnh"));

        verify(productService).removeImage(1L);
    }

    @Test
    void deleteProduct_ShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("Đã xóa sản phẩm"));

        verify(productService).deleteProduct(1L);
    }
}
