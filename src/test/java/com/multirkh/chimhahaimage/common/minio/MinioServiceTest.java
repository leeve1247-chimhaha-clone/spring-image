package com.multirkh.chimhahaimage.common.minio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.multirkh.chimhahaimage.image.resize.ImageResizerService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class MinioServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3PostPolicySigner postPolicySigner;

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
    @DisplayName("getPresignedUrl: PUT presigned URL을 메인 버킷/키로 생성한다")
    void getPresignedUrl_shouldReturnPutPresignedUrl() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("http://presigned-put-url").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        String result = minioService.getPresignedUrl(FILE_NAME);

        assertThat(result).isEqualTo("http://presigned-put-url");
        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        assertThat(captor.getValue().putObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().putObjectRequest().key()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("getPresignedPost: POST policy 서명을 signer에 위임한다")
    void getPresignedPost_shouldDelegateToSigner() {
        Map<String, String> expected = Map.of("policy", "p", "x-amz-signature", "s");
        when(postPolicySigner.presignedPostFields(eq(BUCKET), eq(FILE_NAME))).thenReturn(expected);

        Map<String, String> result = minioService.getPresignedPost(FILE_NAME);

        assertThat(result).isEqualTo(expected);
        verify(postPolicySigner).presignedPostFields(BUCKET, FILE_NAME);
    }

    @Test
    @DisplayName("deleteImage: 메인 버킷에서 단일 이미지를 삭제한다")
    void deleteImage_shouldDeleteFromMainBucket() {
        minioService.deleteImage(FILE_NAME);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("deleteImages: 여러 이미지를 메인 버킷에서 일괄 삭제한다")
    void deleteImages_shouldDeleteMultipleFromMainBucket() {
        Set<String> fileNames = Set.of("file1.jpg", "file2.jpg");
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());

        minioService.deleteImages(fileNames);

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().delete().objects())
                .extracting(ObjectIdentifier::key)
                .containsExactlyInAnyOrder("file1.jpg", "file2.jpg");
    }

    @Test
    @DisplayName("getImage: 메인 버킷에서 이미지 InputStream을 반환한다")
    void getImage_shouldReturnInputStreamFromMainBucket() {
        ResponseInputStream<GetObjectResponse> ris = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream("img".getBytes())));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(ris);

        InputStream result = minioService.getImage(FILE_NAME);

        assertThat(result).isSameAs(ris);
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("createOrRenewUrl: GET presigned URL을 메인 버킷으로 생성한다")
    void createOrRenewUrl_shouldReturnGetPresignedUrlForMainBucket() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("http://presigned-get-url").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String result = minioService.createOrRenewUrl(FILE_NAME);

        assertThat(result).isEqualTo("http://presigned-get-url");
        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("createOrRenewThumbNailUrl: GET presigned URL을 썸네일 버킷으로 생성한다")
    void createOrRenewThumbNailUrl_shouldUseThumbnailBucket() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("http://presigned-thumbnail-url").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String result = minioService.createOrRenewThumbNailUrl(FILE_NAME);

        assertThat(result).isEqualTo("http://presigned-thumbnail-url");
        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo(THUMBNAIL_BUCKET);
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("getType: headObject로 파일의 MIME 타입을 조회한다")
    void getType_shouldReturnContentTypeFromHeadObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentType("image/jpeg").build());

        String result = minioService.getType(FILE_NAME);

        assertThat(result).isEqualTo("image/jpeg");
        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(FILE_NAME);
    }

    @Test
    @DisplayName("createThumbnail: 원본을 리사이즈하여 썸네일 버킷에 업로드하고 URL을 반환한다")
    void createThumbnail_shouldResizeAndUploadToThumbnailBucket() throws Exception {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentType("image/jpeg").build());
        ResponseInputStream<GetObjectResponse> ris = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream("raw".getBytes())));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(ris);
        when(imageResizerService.createResizedImage(any(InputStream.class)))
                .thenReturn(new ByteArrayInputStream("resized".getBytes()));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("http://thumbnail-url").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String result = minioService.createThumbnail(FILE_NAME);

        assertThat(result).isEqualTo("http://thumbnail-url");
        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));
        assertThat(putCaptor.getValue().bucket()).isEqualTo(THUMBNAIL_BUCKET);
        assertThat(putCaptor.getValue().key()).isEqualTo(FILE_NAME);
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("deleteThumbnail: 썸네일 버킷에서 이미지를 삭제한다")
    void deleteThumbnail_shouldDeleteFromThumbnailBucket() {
        minioService.deleteThumbnail(FILE_NAME);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(THUMBNAIL_BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(FILE_NAME);
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
