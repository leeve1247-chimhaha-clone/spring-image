package com.multirkh.chimhahaimage.common.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.multirkh.chimhahaimage.image.resize.ImageResizerService;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PostPolicy;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MinioServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private ImageResizerService imageResizerService;

    @InjectMocks
    private MinioService minioService;

    private static final String BUCKET = "test-bucket";
    private static final String THUMBNAIL_BUCKET = "test-thumbnail-bucket";
    private static final String EXPORT_URL = "http://test-export-url";
    private static final String FILE_NAME = "test-image.jpg";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(minioService, "minioBucketName", BUCKET);
        ReflectionTestUtils.setField(minioService, "thumbnailBucketName", THUMBNAIL_BUCKET);
        ReflectionTestUtils.setField(minioService, "exportUrl", EXPORT_URL);
    }

    @Test
    @DisplayName("getPresignedUrl: PUT 메서드로 메인 버킷에 presigned URL을 생성한다")
    void getPresignedUrl_shouldReturnPutPresignedUrl() throws Exception {
        String expectedUrl = "http://presigned-put-url";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(expectedUrl);

        String result = minioService.getPresignedUrl(FILE_NAME);

        assertThat(result).isEqualTo(expectedUrl);
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().method()).isEqualTo(Method.PUT);
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("getPresignedPost: POST policy 폼 데이터를 반환한다")
    void getPresignedPost_shouldReturnFormDataFields() throws Exception {
        Map<String, String> expectedFields = Map.of("policy", "test-policy");
        when(minioClient.getPresignedPostFormData(any(PostPolicy.class))).thenReturn(expectedFields);

        Map<String, String> result = minioService.getPresignedPost(FILE_NAME);

        assertThat(result).isEqualTo(expectedFields);
        verify(minioClient).getPresignedPostFormData(any(PostPolicy.class));
    }

    @Test
    @DisplayName("deleteImage: 메인 버킷에서 단일 이미지를 삭제한다")
    void deleteImage_shouldDeleteFromMainBucket() throws Exception {
        minioService.deleteImage(FILE_NAME);

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("deleteImages: 여러 이미지를 메인 버킷에서 일괄 삭제한다")
    void deleteImages_shouldDeleteMultipleFromMainBucket() throws Exception {
        Set<String> fileNames = Set.of("file1.jpg", "file2.jpg");
        when(minioClient.removeObjects(any(RemoveObjectsArgs.class))).thenReturn(Collections.emptyList());

        minioService.deleteImages(fileNames);

        ArgumentCaptor<RemoveObjectsArgs> captor = ArgumentCaptor.forClass(RemoveObjectsArgs.class);
        verify(minioClient).removeObjects(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
    }

    @Test
    @DisplayName("getImage: 메인 버킷에서 이미지 InputStream을 반환한다")
    void getImage_shouldReturnInputStreamFromMainBucket() throws Exception {
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        InputStream result = minioService.getImage(FILE_NAME);

        assertThat(result).isEqualTo(mockResponse);
        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("createOrRenewUrl: GET 메서드로 메인 버킷의 presigned URL을 생성한다")
    void createOrRenewUrl_shouldReturnGetPresignedUrlForMainBucket() throws Exception {
        String expectedUrl = "http://presigned-get-url";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(expectedUrl);

        String result = minioService.createOrRenewUrl(FILE_NAME);

        assertThat(result).isEqualTo(expectedUrl);
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().method()).isEqualTo(Method.GET);
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("createOrRenewThumbNailUrl: GET 메서드로 썸네일 버킷의 presigned URL을 생성한다")
    void createOrRenewThumbNailUrl_shouldUseThumbnailBucket() throws Exception {
        String expectedUrl = "http://presigned-thumbnail-url";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(expectedUrl);

        String result = minioService.createOrRenewThumbNailUrl(FILE_NAME);

        assertThat(result).isEqualTo(expectedUrl);
        ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(captor.capture());
        assertThat(captor.getValue().method()).isEqualTo(Method.GET);
        assertThat(captor.getValue().bucket()).isEqualTo(THUMBNAIL_BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("getType: statObject로 파일의 MIME 타입을 조회한다")
    void getType_shouldReturnContentTypeFromStatObject() throws Exception {
        StatObjectResponse mockStat = mock(StatObjectResponse.class);
        when(mockStat.contentType()).thenReturn("image/jpeg");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockStat);

        String result = minioService.getType(FILE_NAME);

        assertThat(result).isEqualTo("image/jpeg");
        ArgumentCaptor<StatObjectArgs> captor = ArgumentCaptor.forClass(StatObjectArgs.class);
        verify(minioClient).statObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("createThumbnail: 원본 이미지를 리사이즈하여 썸네일 버킷에 업로드하고 URL을 반환한다")
    void createThumbnail_shouldResizeAndUploadToThumbnailBucket() throws Exception {
        StatObjectResponse mockStat = mock(StatObjectResponse.class);
        when(mockStat.contentType()).thenReturn("image/jpeg");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockStat);
        GetObjectResponse mockImageStream = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockImageStream);
        InputStream resizedStream = new ByteArrayInputStream("resized".getBytes());
        when(imageResizerService.createResizedImage(any(InputStream.class))).thenReturn(resizedStream);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
        String expectedUrl = "http://thumbnail-url";
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(expectedUrl);

        String result = minioService.createThumbnail(FILE_NAME);

        assertThat(result).isEqualTo(expectedUrl);
        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putCaptor.capture());
        assertThat(putCaptor.getValue().bucket()).isEqualTo(THUMBNAIL_BUCKET);
        assertThat(putCaptor.getValue().object()).isEqualTo(FILE_NAME);
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("deleteThumbnail: 썸네일 버킷에서 이미지를 삭제한다")
    void deleteThumbnail_shouldDeleteFromThumbnailBucket() throws Exception {
        minioService.deleteThumbnail(FILE_NAME);

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(THUMBNAIL_BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("getImageEndPointUrl: exportUrl/bucketName 형식의 URL을 반환한다")
    void getImageEndPointUrl_shouldReturnExportUrlWithBucketName() {
        String result = minioService.getImageEndPointUrl();
        assertThat(result).isEqualTo(EXPORT_URL + "/" + BUCKET);
    }

    @Test
    @DisplayName("getEndPointUrl: exportUrl을 반환한다")
    void getEndPointUrl_shouldReturnExportUrl() {
        assertThat(minioService.getEndPointUrl()).isEqualTo(EXPORT_URL);
    }

    @Test
    @DisplayName("getImageBucket: 메인 버킷명을 반환한다")
    void getImageBucket_shouldReturnMainBucketName() {
        assertThat(minioService.getImageBucket()).isEqualTo(BUCKET);
    }
}
