package com.multirkh.chimhahaimage.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.multirkh.chimhahaimage.common.minio.MinioService;
import com.multirkh.chimhahaimage.image.domain.Image;
import com.multirkh.chimhahaimage.image.dtos.PresignedPostDto;
import com.multirkh.chimhahaimage.image.dtos.PresignedUrlDTO;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private MinioService minioService;

    @InjectMocks
    private ImageService imageService;

    private static final String FILE_NAME = "test-image.jpg";
    private static final String ENDPOINT_URL = "http://test-export-url/test-bucket";
    private static final String PRESIGNED_URL = "http://presigned-url/test-image.jpg";

    // ─── getPresignedUrl ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getPresignedUrl: 고유한 파일명과 PUT용 presigned URL을 반환한다")
    void getPresignedUrl_shouldReturnPresignedUrlDto() {
        when(imageRepository.findByFileName(anyString())).thenReturn(null);
        when(minioService.getPresignedUrl(anyString())).thenReturn(PRESIGNED_URL);

        PresignedUrlDTO result = imageService.getPresignedUrl();

        assertThat(result.getUrl()).isEqualTo(PRESIGNED_URL);
        assertThat(result.getFileName()).isNotBlank();
        verify(minioService).getPresignedUrl(result.getFileName());
    }

    @Test
    @DisplayName("getPresignedPost: 임시 Image를 저장하고 PresignedPostDto를 반환한다")
    void getPresignedPost_shouldSaveTemporaryImageAndReturnDto() {
        when(imageRepository.findByFileName(anyString())).thenReturn(null);
        when(minioService.getImageEndPointUrl()).thenReturn(ENDPOINT_URL);
        Map<String, String> formFields = new HashMap<>(Map.of("policy", "test-policy"));
        when(minioService.getPresignedPost(anyString())).thenReturn(formFields);
        when(imageRepository.save(any(Image.class))).thenAnswer(i -> i.getArgument(0));

        PresignedPostDto result = imageService.getPresignedPost(
                org.springframework.util.MimeType.valueOf("image/jpeg"), null);

        assertThat(result.getUrl()).isEqualTo(ENDPOINT_URL);
        assertThat(result.getFields()).containsKey("key");
        assertThat(result.getFields().get("key")).endsWith(".jpeg");
        assertThat(result.isAlreadyExists()).isFalse();
        verify(imageRepository).save(any(Image.class));
    }

    @Test
    @DisplayName("getPresignedPost: 동일 sha256의 Image가 이미 있으면 alreadyExists=true로 응답하고 새 파일을 생성하지 않는다")
    void getPresignedPost_dedupHit_returnsAlreadyExists() {
        String sha = "abc123def";
        Image existing = new Image("existing.jpeg", "image/jpeg", "url-existing",
                ZonedDateTime.now().plusHours(168), sha);
        when(imageRepository.findBySha256(sha)).thenReturn(existing);

        PresignedPostDto result = imageService.getPresignedPost(
                org.springframework.util.MimeType.valueOf("image/jpeg"), sha);

        assertThat(result.isAlreadyExists()).isTrue();
        assertThat(result.getFields().get("key")).isEqualTo("/existing.jpeg");
        verify(imageRepository, never()).save(any(Image.class));
        verify(minioService, never()).getPresignedPost(anyString());
    }

    @Test
    @DisplayName("getPresignedPost: sha256는 있지만 매칭되는 Image가 없으면 일반 흐름으로 새 presigned를 발급하고 hash를 함께 저장한다")
    void getPresignedPost_dedupMiss_savesWithHash() {
        String sha = "newhash";
        when(imageRepository.findBySha256(sha)).thenReturn(null);
        when(imageRepository.findByFileName(anyString())).thenReturn(null);
        when(minioService.getImageEndPointUrl()).thenReturn(ENDPOINT_URL);
        Map<String, String> formFields = new HashMap<>(Map.of("policy", "p"));
        when(minioService.getPresignedPost(anyString())).thenReturn(formFields);
        when(imageRepository.save(any(Image.class))).thenAnswer(i -> i.getArgument(0));

        PresignedPostDto result = imageService.getPresignedPost(
                org.springframework.util.MimeType.valueOf("image/jpeg"), sha);

        assertThat(result.isAlreadyExists()).isFalse();
        verify(imageRepository).save(org.mockito.ArgumentMatchers.argThat(
                (Image img) -> sha.equals(img.getSha256())));
    }

    // ─── getSrcUrl ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSrcUrl: Image가 있고 만료가 충분히 남은 경우 기존 URL을 반환한다")
    void getSrcUrl_whenImageExistsAndNotExpired_shouldReturnExistingUrl() {
        Image image = new Image(FILE_NAME, "image/jpeg", PRESIGNED_URL,
                ZonedDateTime.now().plusHours(168));
        when(imageRepository.findByFileName(FILE_NAME)).thenReturn(image);

        String result = imageService.getSrcUrl(FILE_NAME);

        assertThat(result).isEqualTo(PRESIGNED_URL);
        verify(minioService, never()).createOrRenewUrl(anyString());
    }

    @Test
    @DisplayName("getSrcUrl: Image가 있고 곧 만료되는 경우 URL을 갱신한다")
    void getSrcUrl_whenImageExistsAndSoonExpired_shouldRenewUrl() {
        String newUrl = "http://new-presigned-url";
        Image image = new Image(FILE_NAME, "image/jpeg", PRESIGNED_URL,
                ZonedDateTime.now().minusMinutes(1));
        when(imageRepository.findByFileName(FILE_NAME)).thenReturn(image);
        when(minioService.createOrRenewUrl(FILE_NAME)).thenReturn(newUrl);

        String result = imageService.getSrcUrl(FILE_NAME);

        assertThat(result).isEqualTo(newUrl);
        verify(minioService).createOrRenewUrl(FILE_NAME);
    }

    @Test
    @DisplayName("getSrcUrl: Image가 없는 경우 새 Image를 저장하고 URL을 반환한다")
    void getSrcUrl_whenImageNotExists_shouldCreateNewImageAndReturnUrl() {
        when(imageRepository.findByFileName(FILE_NAME)).thenReturn(null);
        when(minioService.createOrRenewUrl(FILE_NAME)).thenReturn(PRESIGNED_URL);
        when(minioService.getType(FILE_NAME)).thenReturn("image/jpeg");
        when(imageRepository.save(any(Image.class))).thenAnswer(i -> i.getArgument(0));

        String result = imageService.getSrcUrl(FILE_NAME);

        assertThat(result).isEqualTo(PRESIGNED_URL);
        verify(minioService).createOrRenewUrl(FILE_NAME);
        verify(minioService).getType(FILE_NAME);
        verify(imageRepository).save(any(Image.class));
    }

    // ─── getOrCreateThumbnail ─────────────────────────────────────────────────

    @Test
    @DisplayName("getOrCreateThumbnail: 썸네일이 이미 있으면 기존 썸네일을 반환한다")
    void getOrCreateThumbnail_whenThumbnailExists_shouldReturnExistingThumbnail() {
        Image rawImage = new Image(FILE_NAME, "image/jpeg", PRESIGNED_URL,
                ZonedDateTime.now().plusHours(168));
        Image existingThumbnail = new Image(rawImage, "http://thumbnail-url",
                ZonedDateTime.now().plusHours(168));
        rawImage.setThumbNailImage(existingThumbnail);

        Image result = imageService.getOrCreateThumbnail(rawImage);

        assertThat(result).isEqualTo(existingThumbnail);
        verify(minioService, never()).createThumbnail(anyString());
    }

    @Test
    @DisplayName("getOrCreateThumbnail: 썸네일이 없으면 새로 생성하고 저장한다")
    void getOrCreateThumbnail_whenThumbnailNotExists_shouldCreateThumbnail() {
        Image rawImage = new Image(FILE_NAME, "image/jpeg", PRESIGNED_URL,
                ZonedDateTime.now().plusHours(168));
        String thumbnailUrl = "http://thumbnail-url";
        when(minioService.createThumbnail(FILE_NAME)).thenReturn(thumbnailUrl);
        when(imageRepository.save(any(Image.class))).thenAnswer(i -> i.getArgument(0));

        Image result = imageService.getOrCreateThumbnail(rawImage);

        assertThat(result.getUrl()).isEqualTo(thumbnailUrl);
        assertThat(result.getRawImage()).isEqualTo(rawImage);
        verify(minioService).createThumbnail(FILE_NAME);
        verify(imageRepository).save(any(Image.class));
    }

    // ─── getThumbnailSrcUrl ───────────────────────────────────────────────────

    @Test
    @DisplayName("getThumbnailSrcUrl: 썸네일이 있고 만료가 충분히 남으면 기존 URL을 그대로 반환한다")
    void getThumbnailSrcUrl_whenNotSoonExpired_returnsExistingUrl() {
        Image rawImage = new Image(FILE_NAME, "image/jpeg", PRESIGNED_URL,
                ZonedDateTime.now().plusHours(168));
        Image thumbnail = new Image(rawImage, "http://thumbnail-url",
                ZonedDateTime.now().plusHours(168));
        rawImage.setThumbNailImage(thumbnail);
        when(imageRepository.findByFileName(FILE_NAME)).thenReturn(rawImage);

        String result = imageService.getThumbnailSrcUrl(FILE_NAME);

        assertThat(result).isEqualTo("http://thumbnail-url");
        verify(minioService, never()).createThumbnail(anyString());
        verify(minioService, never()).createOrRenewUrl(anyString());
    }

    @Test
    @DisplayName("getThumbnailSrcUrl: 썸네일이 곧 만료되면 URL을 갱신해 반환한다")
    void getThumbnailSrcUrl_whenSoonExpired_renewsUrl() {
        Image rawImage = new Image(FILE_NAME, "image/jpeg", PRESIGNED_URL,
                ZonedDateTime.now().plusHours(168));
        Image thumbnail = new Image(rawImage, "http://old-thumbnail-url",
                ZonedDateTime.now().minusMinutes(1));
        rawImage.setThumbNailImage(thumbnail);
        when(imageRepository.findByFileName(FILE_NAME)).thenReturn(rawImage);
        when(minioService.createOrRenewUrl(FILE_NAME)).thenReturn("http://renewed-url");

        String result = imageService.getThumbnailSrcUrl(FILE_NAME);

        assertThat(result).isEqualTo("http://renewed-url");
        verify(minioService).createOrRenewUrl(FILE_NAME);
    }
}
