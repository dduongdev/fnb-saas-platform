package com.project.fnb.modules.menu.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.infrastructure.storage.StorageService;
import com.project.fnb.modules.menu.dto.ProductResponse;
import com.project.fnb.modules.menu.entity.Category;
import com.project.fnb.modules.menu.entity.Product;
import com.project.fnb.modules.menu.entity.ProductImage;
import com.project.fnb.modules.menu.repository.CategoryRepository;
import com.project.fnb.modules.menu.repository.ProductImageRepository;
import com.project.fnb.modules.menu.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ProductService productService;

    private static final String TENANT_ID = "tenant-1";
    private Category testCategory;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);

        testCategory = new Category();
        testCategory.setId(1);
        testCategory.setName("Test Category");
        testCategory.setTenantId(TENANT_ID);

        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Test Product");
        testProduct.setPrice(new BigDecimal("100.00"));
        testProduct.setCategory(testCategory);
        testProduct.setStatus(Product.ProductStatus.AVAILABLE);
        testProduct.setImages(new java.util.HashSet<>());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getProducts_ShouldReturnPageOfProductResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(Collections.singletonList(testProduct));
        
        // Mock the new method that includes images (N+1 fix)
        when(productRepository.findAllWithImages(pageable))
                .thenReturn(page);

        Page<ProductResponse> result = productService.getProducts(null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Test Product", result.getContent().get(0).getName());
    }

    @Test
    void getProductDetail_WhenExists_ShouldReturnResponse() {
        when(productRepository.findByIdWithImages(1L)).thenReturn(Optional.of(testProduct));

        ProductResponse result = productService.getProductDetail(1L);

        assertNotNull(result);
        assertEquals("Test Product", result.getName());
    }

    @Test
    void getProductDetail_WhenNotExists_ShouldThrowException() {
        when(productRepository.findByIdWithImages(1L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> productService.getProductDetail(1L));
        assertEquals(404, ex.getErrorCode());
    }

    @Test
    void createProduct_ShouldSaveProductAndImages() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(testCategory));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);
        
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        List<MultipartFile> images = Collections.singletonList(image);
        
        when(productImageRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(storageService.uploadTenantImage(image)).thenReturn("http://image.url");

        ProductResponse result = productService.createProduct(1, "New Product", new BigDecimal("50.00"), "Desc", images);

        assertNotNull(result);
        verify(productRepository, times(1)).save(any(Product.class)); // Saved initially and then after setting images
        verify(storageService).uploadTenantImage(image);
    }
    
    @Test
    void createProduct_InvalidCategory_ShouldThrowException() {
        when(categoryRepository.findById(1)).thenReturn(Optional.empty());
        List<MultipartFile> images = new ArrayList<>();
        
        AppException ex = assertThrows(AppException.class, () -> 
            productService.createProduct(1, "New Product", new BigDecimal("50.00"), "Desc", images)
        );
        assertEquals(404, ex.getErrorCode());
    }

    @Test
    void updateProductInfo_ShouldUpdateFields() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(categoryRepository.findById(2)).thenReturn(Optional.of(testCategory));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        ProductResponse result = productService.updateProductInfo(1L, "Updated File", new BigDecimal("150.00"), "New Desc", 2, Product.ProductStatus.OUT_OF_STOCK);

        assertNotNull(result);
        assertEquals("Updated File", testProduct.getName()); // It modified the mock object directly
        verify(productRepository).save(testProduct);
    }

    @Test
    void addImages_ShouldUploadAndAddImagesToProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        List<MultipartFile> images = Arrays.asList(image);
        when(productImageRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(storageService.uploadTenantImage(image)).thenReturn("http://image.url");

        productService.addImages(1L, images);

        verify(storageService).uploadTenantImage(image);
        verify(productRepository).save(testProduct);
        System.out.println("Size: " + testProduct.getImages().size()); assertEquals(1, testProduct.getImages().size());
    }

    @Test
void removeImage_ShouldDeleteImage() {
        productService.removeImage(1L);

        verify(productImageRepository).deleteById(1L);
    }

    @Test
    void deleteProduct_ShouldHardDelete() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        productService.deleteProduct(1L);

        verify(productRepository).delete(testProduct);
    }

    @Test
    void getProducts_WithCategoryIdAndStatus_ShouldCallCorrectRepositoryMethod() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByCategoryIdAndStatusWithImages(1, Product.ProductStatus.AVAILABLE, pageable))
                .thenReturn(new PageImpl<>(Collections.singletonList(testProduct)));

        productService.getProducts(1, Product.ProductStatus.AVAILABLE, pageable);

        verify(productRepository).findByCategoryIdAndStatusWithImages(1, Product.ProductStatus.AVAILABLE, pageable);
    }

    @Test
    void getProducts_WithCategoryIdOnly_ShouldCallCorrectRepositoryMethod() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByCategoryIdWithImages(1, pageable))
                .thenReturn(new PageImpl<>(Collections.singletonList(testProduct)));

        productService.getProducts(1, null, pageable);

        verify(productRepository).findByCategoryIdWithImages(1, pageable);
    }

    @Test
    void getProducts_WithStatusOnly_ShouldCallCorrectRepositoryMethod() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByStatusWithImages(Product.ProductStatus.AVAILABLE, pageable))
                .thenReturn(new PageImpl<>(Collections.singletonList(testProduct)));

        productService.getProducts(null, Product.ProductStatus.AVAILABLE, pageable);

        verify(productRepository).findByStatusWithImages(Product.ProductStatus.AVAILABLE, pageable);
    }

    @Test
    void createProduct_WithEmptyFile_ShouldIgnoreFile() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(testCategory));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);
        when(productImageRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        MultipartFile emptyFile = mock(MultipartFile.class);
        when(emptyFile.isEmpty()).thenReturn(true);

        productService.createProduct(1, "Name", BigDecimal.TEN, "Desc", Collections.singletonList(emptyFile));

        verify(storageService, never()).uploadTenantImage(any());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void updateProductInfo_InvalidCategory_ShouldThrowException() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(categoryRepository.findById(99)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                productService.updateProductInfo(1L, "Name", BigDecimal.TEN, "Desc", 99, Product.ProductStatus.AVAILABLE));

        assertEquals(404, ex.getErrorCode());
        assertEquals("Danh mục không tồn tại", ex.getMessage());
    }

    @Test
    void updateProductInfo_NullFields_ShouldNotUpdateFields() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        productService.updateProductInfo(1L, null, null, null, null, null);

        assertEquals("Test Product", testProduct.getName()); // No change
        verify(productRepository).save(testProduct);
    }

    @Test
    void addImages_InvalidProductId_ShouldThrowException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                productService.addImages(99L, Collections.emptyList()));

        assertEquals(404, ex.getErrorCode());
    }

    @Test
    void addImages_NullOrEmptyFiles_ShouldDoNothing() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        productService.addImages(1L, null);
        productService.addImages(1L, Collections.emptyList());

        verify(productImageRepository, never()).saveAll(anyList());
    }

    @Test
    void deleteProduct_InvalidProductId_ShouldThrowException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                productService.deleteProduct(99L));

        assertEquals(404, ex.getErrorCode());
    }
}
