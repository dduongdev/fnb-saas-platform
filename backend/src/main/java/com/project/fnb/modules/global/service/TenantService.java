package com.project.fnb.modules.global.service;

import com.project.fnb.modules.global.dto.CreateTenantRequest;
import com.project.fnb.modules.global.entity.Tenant;
import com.project.fnb.modules.global.repository.TenantRepository;
import com.project.fnb.modules.menu.service.CategoryService;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final CategoryService categoryService;
    private final MinioClient minioClient;

    @Transactional
    public Tenant createTenant(CreateTenantRequest request, String ownerId) {
        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .address(request.getAddress())
                .ownerId(ownerId)
                .isActive(true)
                .build();
        
        tenant = tenantRepository.save(tenant);

        // 2. Tạo Bucket MinIO riêng cho quán: tenant-{id}-assets
        // Tên bucket quy ước: tenant-<uuid>-assets
        String bucketName = "tenant-" + tenant.getId().toLowerCase() + "-assets";
        createBucketSafe(bucketName);

        // 3. Tạo danh mục mặc định "Khác" cho quán mới
        categoryService.createDefaultCategory(tenant.getId());

        return tenant;
    }

    private void createBucketSafe(String bucketName) {
        try {
            boolean found = minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created bucket for tenant: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to create bucket: {}", bucketName, e);
        }
    }
}