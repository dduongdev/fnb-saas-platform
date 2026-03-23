package com.project.fnb.modules.global.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.infrastructure.storage.StorageService;
import com.project.fnb.modules.global.dto.CreateTenantRequest;
import com.project.fnb.modules.global.dto.PaymentConfigDto;
import com.project.fnb.modules.global.dto.UpdateTenantRequest;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.global.repository.UserRepository;
import com.project.fnb.modules.menu.service.CategoryService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private MinioClient minioClient;

    @Mock
    private CategoryService categoryService;

    @Mock
    private StorageService storageService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TenantService tenantService;

    @Captor
    private ArgumentCaptor<Tenant> tenantCaptor;

    private Tenant testTenant;
    private final String OWNER_ID = "owner-123";
    private final String TENANT_ID = "tenant-1";

    @BeforeEach
    void setUp() {
        testTenant = Tenant.builder()
                .id(TENANT_ID)
                .name("Old Name")
                .address("Old Address")
                .ownerId(OWNER_ID)
                .isActive(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createTenant_WithLogo_ShouldCreateTenantAndBucketAndUploadLogo() throws Exception {
        // Arrange
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName("New Tenant");
        request.setAddress("New Address");
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.jpg", "image/jpeg", "image".getBytes());
        String logoUrl = "http://example.com/logo.jpg";
        
        // Mock ID generation on first save
        Tenant savedInitialTenant = Tenant.builder()
                .id("generated-id")
                .name("New Tenant")
                .address("New Address")
                .ownerId(OWNER_ID)
                .isActive(true)
                .build();

        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedInitialTenant);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        when(storageService.uploadTenantImage(logo)).thenReturn(logoUrl);

        // Act
        Tenant result = tenantService.createTenant(request, OWNER_ID, logo);

        // Assert
        assertNotNull(result);
        assertEquals("generated-id", result.getId());
        verify(tenantRepository, times(2)).save(any(Tenant.class)); 
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).setBucketPolicy(any(SetBucketPolicyArgs.class));
        verify(storageService).uploadTenantImage(logo);
        verify(categoryService).createDefaultCategory("generated-id");
        assertNull(TenantContext.getTenantId()); // Ensure context is cleared
    }

    @Test
    void createTenant_WithoutLogo_ShouldCreateTenantAndBucketWithoutUpload() throws Exception {
        // Arrange
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName("New Tenant");
        request.setAddress("New Address");
        
        Tenant savedInitialTenant = Tenant.builder()
                .id("generated-id")
                .build();

        when(tenantRepository.save(any(Tenant.class))).thenReturn(savedInitialTenant);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // Act
        Tenant result = tenantService.createTenant(request, OWNER_ID, null);

        // Assert
        assertNotNull(result);
        verify(tenantRepository, times(1)).save(any(Tenant.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        verify(storageService, never()).uploadTenantImage(any());
        verify(categoryService).createDefaultCategory("generated-id");
    }

    @Test
    void updateTenant_WhenAuthorizedAndNoLogo_ShouldUpdateDetails() {
        // Arrange
        UpdateTenantRequest request = new UpdateTenantRequest();
        request.setName("Updated Name");
        request.setAddress("Updated Address");
        
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));
        when(tenantRepository.save(any(Tenant.class))).thenReturn(testTenant);

        // Act
        Tenant result = tenantService.updateTenant(TENANT_ID, request, null, OWNER_ID);

        // Assert
        assertEquals("Updated Name", testTenant.getName());
        assertEquals("Updated Address", testTenant.getAddress());
        verify(tenantRepository).save(testTenant);
        verify(storageService, never()).uploadTenantImage(any());
    }

    @Test
    void updateTenant_WhenAuthorizedWithLogo_ShouldUpdateDetailsAndLogo() {
        // Arrange
        testTenant.setLogoUrl("old-logo-url");
        UpdateTenantRequest request = new UpdateTenantRequest();
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.jpg", "image/jpeg", "image".getBytes());
        String newLogoUrl = "new-logo-url";
        
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));
        when(storageService.uploadTenantImage(logo)).thenReturn(newLogoUrl);
        when(tenantRepository.save(any(Tenant.class))).thenReturn(testTenant);

        // Act
        Tenant result = tenantService.updateTenant(TENANT_ID, request, logo, OWNER_ID);

        // Assert
        assertEquals(newLogoUrl, testTenant.getLogoUrl());
        verify(storageService).deleteFile("old-logo-url");
        verify(storageService).uploadTenantImage(logo);
        verify(tenantRepository).save(testTenant);
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void updateTenant_WhenNotAuthorized_ShouldThrowAppException() {
        // Arrange
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));

        // Act & Assert
        AppException ex = assertThrows(AppException.class, () -> {
            tenantService.updateTenant(TENANT_ID, new UpdateTenantRequest(), null, "wrong-user");
        });
        assertEquals(403, ex.getErrorCode());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void getMyTenants_ShouldReturnTenantListForOwner() {
        // Arrange
        Tenant t1 = Tenant.builder().id("1").ownerId(OWNER_ID).build();
        Tenant t2 = Tenant.builder().id("2").ownerId("other-user").build();
        Tenant t3 = Tenant.builder().id("3").ownerId(OWNER_ID).build();
        
        when(tenantRepository.findAll()).thenReturn(Arrays.asList(t1, t2, t3));

        // Act
        List<Tenant> result = tenantService.getMyTenants(OWNER_ID);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(t -> t.getOwnerId().equals(OWNER_ID)));
    }

    @Test
    void getTenantDetail_WhenExists_ShouldReturnTenant() {
        // Arrange
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));

        // Act
        Tenant result = tenantService.getTenantDetail(TENANT_ID);

        // Assert
        assertNotNull(result);
        assertEquals(TENANT_ID, result.getId());
    }

    @Test
    void getTenantDetail_WhenNotExists_ShouldThrowAppException() {
        // Arrange
        when(tenantRepository.findById("non-existent")).thenReturn(Optional.empty());

        // Act & Assert
        AppException ex = assertThrows(AppException.class, () -> {
            tenantService.getTenantDetail("non-existent");
        });
        assertEquals(404, ex.getErrorCode());
    }

    @Test
    void updateTenantStatus_WhenAuthorized_ShouldUpdateStatus() {
        // Arrange
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));

        // Act
        tenantService.updateTenantStatus(TENANT_ID, false, OWNER_ID);

        // Assert
        assertFalse(testTenant.getIsActive());
        verify(tenantRepository).save(testTenant);
    }
    
    @Test
    void updatePaymentConfig_WhenAuthorized_ShouldBeSuccessful() {
        // Arrange
        PaymentConfigDto config = new PaymentConfigDto();
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));
        
        // Act
        tenantService.updatePaymentConfig(TENANT_ID, config, OWNER_ID);
        
        // Assert
        verify(tenantRepository).findById(TENANT_ID);
        assertNotNull(testTenant.getPaymentConfig());
    }

    @Test
    void updateTenantStatus_WhenNotAuthorized_ShouldThrowAppException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));

        AppException ex = assertThrows(AppException.class, () -> {
            tenantService.updateTenantStatus(TENANT_ID, false, "wrong-user");
        });
        assertEquals(403, ex.getErrorCode());
    }

    @Test
    void updatePaymentConfig_WhenNotAuthorized_ShouldThrowAppException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));

        AppException ex = assertThrows(AppException.class, () -> {
            tenantService.updatePaymentConfig(TENANT_ID, new PaymentConfigDto(), "wrong-user");
        });
        assertEquals(403, ex.getErrorCode());
    }

    @Test
    void updatePaymentConfig_WhenVNPayMissingCodeOrSecret_ShouldThrowAppException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));

        PaymentConfigDto config = new PaymentConfigDto();
        PaymentConfigDto.VNPayConfig vnpReq = new PaymentConfigDto.VNPayConfig();
        vnpReq.setEnabled(true);
        config.setVnpay(vnpReq);

        AppException ex = assertThrows(AppException.class, () -> {
            tenantService.updatePaymentConfig(TENANT_ID, config, OWNER_ID);
        });
        assertEquals(400, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("TmnCode và HashSecret"));
    }

    @Test
    void updatePaymentConfig_WhenVNPayValid_ShouldSave() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));

        PaymentConfigDto config = new PaymentConfigDto();
        PaymentConfigDto.VNPayConfig vnpReq = new PaymentConfigDto.VNPayConfig();
        vnpReq.setEnabled(true);
        vnpReq.setTmnCode("TMN");
        vnpReq.setHashSecret("SECRET");
        config.setVnpay(vnpReq);

        tenantService.updatePaymentConfig(TENANT_ID, config, OWNER_ID);

        verify(tenantRepository).save(testTenant);
        assertNotNull(testTenant.getPaymentConfig().getVnpay());
        assertEquals("TMN", testTenant.getPaymentConfig().getVnpay().getTmnCode());
    }

    @Test
    void updatePaymentConfig_WhenMomoMissingKeys_ShouldThrowAppException() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));

        PaymentConfigDto config = new PaymentConfigDto();
        PaymentConfigDto.MomoConfig momoReq = new PaymentConfigDto.MomoConfig();
        momoReq.setEnabled(true);
        config.setMomo(momoReq);

        AppException ex = assertThrows(AppException.class, () -> {
            tenantService.updatePaymentConfig(TENANT_ID, config, OWNER_ID);
        });
        assertEquals(400, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Momo"));
    }

    @Test
    void updatePaymentConfig_WhenMomoValid_ShouldSave() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));

        PaymentConfigDto config = new PaymentConfigDto();
        PaymentConfigDto.MomoConfig momoReq = new PaymentConfigDto.MomoConfig();
        momoReq.setEnabled(true);
        momoReq.setPartnerCode("PC");
        momoReq.setAccessKey("AK");
        momoReq.setSecretKey("SK");
        config.setMomo(momoReq);

        tenantService.updatePaymentConfig(TENANT_ID, config, OWNER_ID);

        verify(tenantRepository).save(testTenant);
        assertNotNull(testTenant.getPaymentConfig().getMomo());
        assertEquals("PC", testTenant.getPaymentConfig().getMomo().getPartnerCode());
    }

    @Test
    void updateTenant_WhenBothNameAndAddressNull_ShouldNotUpdateFields() {
        UpdateTenantRequest request = new UpdateTenantRequest();
        request.setName(null);
        request.setAddress(null);
        
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(testTenant));
        when(tenantRepository.save(any(Tenant.class))).thenReturn(testTenant);

        tenantService.updateTenant(TENANT_ID, request, null, OWNER_ID);

        assertEquals("Old Name", testTenant.getName());
        assertEquals("Old Address", testTenant.getAddress());
        verify(tenantRepository).save(testTenant);
    }
}