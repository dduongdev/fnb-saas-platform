package com.project.fnb.infrastructure.storage;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.security.TenantContext;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "externalUrl", "http://localhost:9000");
        ReflectionTestUtils.setField(storageService, "internalUrl", "http://minio:9000");
        ReflectionTestUtils.setField(storageService, "userBucket", "user-profiles");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void uploadTenantImage_ShouldThrow_WhenTenantMissing() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        AppException ex = assertThrows(AppException.class, () -> storageService.uploadTenantImage(file));

        assertEquals(400, ex.getErrorCode());
        assertEquals("Tenant ID missing", ex.getMessage());
    }

    @Test
    void uploadTenantImage_ShouldUploadWithTenantBucket() throws Exception {
        TenantContext.setTenantId("SHOP-01");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        String url = storageService.uploadTenantImage(file);

        assertTrue(url.startsWith("http://localhost:9000/tenant-shop-01-assets/images/"));
        assertTrue(url.endsWith("-photo.jpg"));
        verify(minioClient).putObject(any());
    }

    @Test
    void deleteFile_ShouldCallMinio_WhenUrlValid() throws Exception {
        storageService.deleteFile("http://localhost:9000/user-profiles/avatars/a.png");

        verify(minioClient).removeObject(any());
    }

    @Test
    void deleteFile_ShouldIgnore_WhenUrlBlank() throws Exception {
        storageService.deleteFile("  ");

        verify(minioClient, never()).removeObject(any());
    }
}
