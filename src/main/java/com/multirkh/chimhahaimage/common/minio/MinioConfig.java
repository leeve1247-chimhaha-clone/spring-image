package com.multirkh.chimhahaimage.common.minio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.DeleteBucketCorsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketCorsArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.messages.CORSConfiguration;
import io.minio.messages.CORSConfiguration.CORSRule;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MinioConfig {
    @Value("${spa.web.origin}")
    private String spaUrl;

    @Value("${minio.export-url}")
    private String minioExportUrl;

    @Value("${minio.access-key}")
    private String minioAccessKey;

    @Value("${minio.secret-key}")
    private String minioSecretKey;

    @Value("${minio.bucket-name}")
    private String minioBucketName;
    @Value("${minio.thumbnail-bucket-name}")
    private String thumbnailBucketName;

    final ObjectMapper objectMapper;

    public MinioConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public MinioClient minioClient() {
        MinioClient minioClient = initMinioConfig();
        Map<String, Boolean> minioBuckets = initMinioBuckets(minioClient);
        for (Map.Entry<String, Boolean> entry : minioBuckets.entrySet()) {
            try {
                minioClient.deleteBucketCors(DeleteBucketCorsArgs.builder().bucket(entry.getKey()).build());
                CORSConfiguration config = getCorsConfiguration();
                minioClient.setBucketCors(
                        SetBucketCorsArgs.builder().bucket(entry.getKey()).config(config).build());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return minioClient;
    }

    private Map<String, Boolean> initMinioBuckets(MinioClient minioClient) {
        try {
            Map<String, Boolean> bucketDoesExists = new HashMap<>();
            bucketDoesExists.put(minioBucketName,
                    minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucketName).build()));
            bucketDoesExists.put(thumbnailBucketName,
                    minioClient.bucketExists(BucketExistsArgs.builder().bucket(thumbnailBucketName).build()));
            for (Map.Entry<String, Boolean> entry : bucketDoesExists.entrySet()) {
                if (!entry.getValue()) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(entry.getKey()).build());
                } else {
                    log.info("Bucket '{}' already exists.", entry.getKey());
                }
            }
            initBucketPolicy(minioClient);
            return bucketDoesExists;
        } catch (Exception e) {
            throw new RuntimeException("Error occurred while creating minio client", e);
        }
    }

    private String createPublicReadAccessJsonPolicy(String minioBucketName) {
        try {
            Map<String, Object> policyMap = Map.of(
                    "Version", "2012-10-17",
                    "Statement", List.of(
                            Map.of(
                                    "Sid", "PublicReadGetObject",
                                    "Effect", "Allow",
                                    "Principal", "*",
                                    "Action", List.of("s3:GetObject"),
                                    "Resource", List.of("arn:aws:s3:::" + minioBucketName + "/*")
                            )
                    )
            );
            return objectMapper.writeValueAsString(policyMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    private void initBucketPolicy(MinioClient minioClient) {
        try {
            String policyJson = createPublicReadAccessJsonPolicy(minioBucketName);
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder().bucket(minioBucketName).config(policyJson).build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MinioClient initMinioConfig() {
        try {
            return MinioClient.builder()
                    .endpoint(minioExportUrl)
                    .credentials(minioAccessKey, minioSecretKey)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Minio Client Error");
        }
    }

    private CORSConfiguration getCorsConfiguration() {
        List<String> allowedOrigins = Collections.singletonList(spaUrl);
        return new CORSConfiguration(
                List.of(new CORSRule(
                        List.of("*"),
                        Arrays.asList("PUT", "POST"),
                        allowedOrigins,
                        null,
                        null,
                        3000))
        );
    }
}
