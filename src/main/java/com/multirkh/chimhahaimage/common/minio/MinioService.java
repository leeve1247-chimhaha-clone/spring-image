package com.multirkh.chimhahaimage.common.minio;

import com.multirkh.chimhahaimage.image.resize.ImageResizerService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {
    private final ImageResizerService imageResizerService;
    private final MinioClient minioClient;
    @Value("${minio.bucket-name}")
    private String minioBucketName;
    @Value("${minio.thumbnail-bucket-name}")
    private String thumbnailBucketName;
    @Value("${minio.export-url}")
    private String exportUrl;

    public String getPresignedUrl(String randomImageName) {
        try {
            return minioClient
                    .getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
                                    .method(Method.PUT)
                                    .bucket(minioBucketName)
                                    .object(randomImageName)
                                    .expiry(15, TimeUnit.MINUTES)
                                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getImageEndPointUrl(){
        return String.join("/", List.of(exportUrl, minioBucketName));
    }

    public Map<String, String> getPresignedPost(String fileName){
        try{
            PostPolicy postPolicy = new PostPolicy(minioBucketName, ZonedDateTime.now().plusMinutes(15));
            postPolicy.addEqualsCondition("key", fileName);
            postPolicy.addContentLengthRangeCondition(1, 10*1024*1024);
            return minioClient.getPresignedPostFormData(postPolicy);
        } catch (Exception e) {
            throw  new RuntimeException(e);
        }
    }

    public void deleteImages(Set<String> fileNames) {
        List<DeleteObject> objects = new LinkedList<>();
        for (String fileName : fileNames) {
            objects.add(new DeleteObject(fileName));
        }
        try {
            Iterable<Result<DeleteError>> results =
                    minioClient.removeObjects(
                            RemoveObjectsArgs.builder().bucket(minioBucketName).objects(objects).build());
            for (Result<DeleteError> result : results) {
                DeleteError error = result.get();
                System.out.println(
                        "Error in deleting object " + error.objectName() + "; " + error.message());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteImage(String fileName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioBucketName)
                            .object(fileName)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream getImage(String fileName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioBucketName)
                            .object(fileName)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String createThumbnail(String fileName) {
        try {
            String mimeType = getType(fileName);
            InputStream rawImage = getImage(fileName);
            InputStream resizedImageInputStream = imageResizerService.createResizedImage(rawImage);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(thumbnailBucketName)
                            .object(fileName)
                            .stream(resizedImageInputStream, -1, 10485760)
                            .contentType(mimeType)
                            .build());
            return createOrRenewThumbNailUrl(fileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteThumbnail(String fileName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(thumbnailBucketName)
                            .object(fileName)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String createOrRenewUrl(String fileName) {
        try {
            String presignedObjectUrl = minioClient
                    .getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
                                    .method(Method.GET)
                                    .bucket(minioBucketName)
                                    .object(fileName)
                                    .expiry(7, TimeUnit.DAYS)
                                    .build());
            return presignedObjectUrl;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String createOrRenewThumbNailUrl(String fileName) {
        try {
            return minioClient
                    .getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
                                    .method(Method.GET)
                                    .bucket(thumbnailBucketName)
                                    .object(fileName)
                                    .expiry(7, TimeUnit.DAYS)
                                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getType(String fileName) {
        try {
            StatObjectResponse statObjectResponse = minioClient
                    .statObject(StatObjectArgs
                            .builder()
                            .bucket(minioBucketName)
                            .object(fileName)
                            .build()
                    );
            return statObjectResponse.contentType();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getEndPointUrl() {
        return exportUrl;
    }

    public String getImageBucket() {
        return minioBucketName;
    }
}
