package com.project.fnb.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinioInitializer implements ApplicationRunner {

    private final MinioClient minioClient;

    @Value("${minio.buckets.public-assets}")
    private String publicBucket;

    @Value("${minio.buckets.user-profiles}")
    private String userProfilesBucket;

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 Starting MinIO Bucket Initialization...");
        
        // 1. Bucket public toàn bộ (Cho ảnh món ăn, logo quán)
        createBucket(publicBucket, getPublicPolicy(publicBucket));

        // 2. Bucket User: Chỉ public folder "avatars/", còn lại (như CV) thì Private
        createBucket(userProfilesBucket, getUserProfilePolicy(userProfilesBucket));
    }

    private void createBucket(String bucketName, String policyJson) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created bucket: {}", bucketName);
            }
            
            if (policyJson != null) {
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder().bucket(bucketName).config(policyJson).build()
                );
                log.info("Updated policy for bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Error initializing bucket {}: {}", bucketName, e.getMessage());
        }
    }

    // Policy 1: Public toàn bộ bucket
    private String getPublicPolicy(String bucketName) {
        return """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Principal": {"AWS": ["*"]},
                  "Action": ["s3:GetObject"],
                  "Resource": ["arn:aws:s3:::%s/*"]
                }
              ]
            }
            """.formatted(bucketName);
    }

    // Policy 2: Chỉ Public folder "avatars/"
    private String getUserProfilePolicy(String bucketName) {
        return """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Principal": {"AWS": ["*"]},
                  "Action": ["s3:GetObject"],
                  "Resource": ["arn:aws:s3:::%s/avatars/*"]
                }
              ]
            }
            """.formatted(bucketName);
    }
}