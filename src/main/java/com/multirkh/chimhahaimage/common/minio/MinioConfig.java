package com.multirkh.chimhahaimage.common.minio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

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

    @Value("${minio.region}")
    private String region;

    @Value("${minio.bucket-name}")
    private String minioBucketName;
    @Value("${minio.thumbnail-bucket-name}")
    private String thumbnailBucketName;

    final ObjectMapper objectMapper;

    public MinioConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public S3Client s3Client() {
        S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(minioExportUrl))
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(pathStyleConfig())
                .build();
        initBuckets(s3Client);
        return s3Client;
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(minioExportUrl))
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(pathStyleConfig())
                .build();
    }

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(minioAccessKey, minioSecretKey));
    }

    // SeaweedFS는 path-style 접근만 지원 (가상 호스팅 스타일 미지원).
    private S3Configuration pathStyleConfig() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }

    private void initBuckets(S3Client s3Client) {
        Map<String, Boolean> bucketExists = new HashMap<>();
        bucketExists.put(minioBucketName, bucketExists(s3Client, minioBucketName));
        bucketExists.put(thumbnailBucketName, bucketExists(s3Client, thumbnailBucketName));
        for (Map.Entry<String, Boolean> entry : bucketExists.entrySet()) {
            if (!entry.getValue()) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(entry.getKey()).build());
            } else {
                log.info("Bucket '{}' already exists.", entry.getKey());
            }
        }
        initBucketPolicy(s3Client);
        for (String bucket : bucketExists.keySet()) {
            applyCors(s3Client, bucket);
        }
    }

    private boolean bucketExists(S3Client s3Client, String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private void applyCors(S3Client s3Client, String bucket) {
        s3Client.deleteBucketCors(DeleteBucketCorsRequest.builder().bucket(bucket).build());
        CORSRule rule = CORSRule.builder()
                .allowedHeaders("*")
                .allowedMethods("PUT", "POST")
                .allowedOrigins(spaUrl)
                .maxAgeSeconds(3000)
                .build();
        s3Client.putBucketCors(PutBucketCorsRequest.builder()
                .bucket(bucket)
                .corsConfiguration(CORSConfiguration.builder().corsRules(rule).build())
                .build());
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

    private void initBucketPolicy(S3Client s3Client) {
        String policyJson = createPublicReadAccessJsonPolicy(minioBucketName);
        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(minioBucketName)
                .policy(policyJson)
                .build());
    }
}
