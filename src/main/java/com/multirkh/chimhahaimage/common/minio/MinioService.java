package com.multirkh.chimhahaimage.common.minio;

import com.multirkh.chimhahaimage.image.resize.ImageResizerService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {
    private final ImageResizerService imageResizerService;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3PostPolicySigner postPolicySigner;
    @Value("${minio.bucket-name}")
    private String minioBucketName;
    @Value("${minio.thumbnail-bucket-name}")
    private String thumbnailBucketName;
    @Value("${minio.export-url}")
    private String exportUrl;

    public String getPresignedUrl(String randomImageName) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(minioBucketName)
                .key(randomImageName)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(objectRequest)
                .build();
        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    public String getImageEndPointUrl() {
        return String.join("/", List.of(exportUrl, minioBucketName));
    }

    public Map<String, String> getPresignedPost(String fileName) {
        return postPolicySigner.presignedPostFields(minioBucketName, fileName);
    }

    public void deleteImages(Set<String> fileNames) {
        List<ObjectIdentifier> objects = new ArrayList<>();
        for (String fileName : fileNames) {
            objects.add(ObjectIdentifier.builder().key(fileName).build());
        }
        DeleteObjectsResponse response = s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                        .bucket(minioBucketName)
                        .delete(Delete.builder().objects(objects).build())
                        .build());
        for (S3Error error : response.errors()) {
            log.error("Error in deleting object {}; {}", error.key(), error.message());
        }
    }

    public void deleteImage(String fileName) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(minioBucketName)
                .key(fileName)
                .build());
    }

    public InputStream getImage(String fileName) {
        return s3Client.getObject(GetObjectRequest.builder()
                .bucket(minioBucketName)
                .key(fileName)
                .build());
    }

    public String createThumbnail(String fileName) {
        try {
            String mimeType = getType(fileName);
            InputStream rawImage = getImage(fileName);
            InputStream resizedImageInputStream = imageResizerService.createResizedImage(rawImage);
            byte[] resized = resizedImageInputStream.readAllBytes();
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(thumbnailBucketName)
                            .key(fileName)
                            .contentType(mimeType)
                            .build(),
                    RequestBody.fromBytes(resized));
            return createOrRenewThumbNailUrl(fileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteThumbnail(String fileName) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(thumbnailBucketName)
                .key(fileName)
                .build());
    }

    public String createOrRenewUrl(String fileName) {
        return presignGet(minioBucketName, fileName);
    }

    public String createOrRenewThumbNailUrl(String fileName) {
        return presignGet(thumbnailBucketName, fileName);
    }

    private String presignGet(String bucket, String fileName) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofDays(7))
                .getObjectRequest(objectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public String getType(String fileName) {
        HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(minioBucketName)
                .key(fileName)
                .build());
        return head.contentType();
    }

    public String getEndPointUrl() {
        return exportUrl;
    }

    public String getImageBucket() {
        return minioBucketName;
    }
}
