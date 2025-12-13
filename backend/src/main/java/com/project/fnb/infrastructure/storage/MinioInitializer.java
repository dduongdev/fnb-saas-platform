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
        log.info("Starting MinIO Bucket Initialization...");
        createBucket(publicBucket, true);
        createBucket(userProfilesBucket, false);
    }

    private void createBucket(String bucketName, boolean isPublic) {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created bucket: {}", bucketName);

                if (isPublic) {
                    String policy = getPublicReadPolicy(bucketName);
                    minioClient.setBucketPolicy(
                            SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build()
                    );
                    log.info("Set PUBLIC policy for bucket: {}", bucketName);
                }
            }
        } catch (Exception e) {
            log.error("Error initializing bucket {}: {}", bucketName, e.getMessage());
        }
    }

    private String getPublicReadPolicy(String bucketName) {
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
}