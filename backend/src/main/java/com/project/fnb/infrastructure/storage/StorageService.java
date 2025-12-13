package com.project.fnb.infrastructure.storage;

import com.project.fnb.infrastructure.security.TenantContext;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.external-url}")
    private String minioExternalUrl;

    /**
     * Upload file lên bucket riêng của Tenant
     * @param file File ảnh
     * @return Public URL của ảnh
     */
    public String uploadTenantImage(MultipartFile file) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("Tenant ID is missing in context!");
        }

        // Tên bucket theo quy ước lúc tạo quán: tenant-{id}-assets
        String bucketName = "tenant-" + tenantId.toLowerCase() + "-assets";
        
        // Tên file: images/uuid-filename.jpg
        String fileName = "images/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return String.format("%s/%s/%s", minioExternalUrl, bucketName, fileName);

        } catch (Exception e) {
            log.error("Failed to upload file to MinIO", e);
            throw new RuntimeException("Upload failed: " + e.getMessage());
        }
    }
}