package com.multirkh.chimhahaimage.image;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.multirkh.chimhahaimage.common.minio.MinioService;
import com.multirkh.chimhahaimage.image.domain.Image;
import com.multirkh.chimhahaimage.image.domain.PostImage;
import java.time.ZonedDateTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

    @Mock
    private ImageRepository imageRepository;
    @Mock
    private PostImageRepository postImageRepository;
    @Mock
    private MinioService minioService;
    @InjectMocks
    private PostImageService postImageService;

    private static Image rawImage(String fileName) {
        return new Image(fileName, "png", "http://oss/" + fileName, ZonedDateTime.now().plusHours(167));
    }

    @Test
    @DisplayName("linkImagesToPost: 존재하는 이미지마다 PostImage 저장")
    void linkImagesToPost() {
        Image a = rawImage("a.png");
        Image b = rawImage("b.png");
        when(imageRepository.findByFileNames(Set.of("a.png", "b.png"))).thenReturn(Set.of(a, b));

        postImageService.linkImagesToPost(42L, Set.of("a.png", "b.png"));

        verify(postImageRepository, org.mockito.Mockito.times(2)).save(any(PostImage.class));
    }

    @Test
    @DisplayName("linkImagesToPost: 빈 입력 → 아무것도 안 함")
    void linkImagesToPost_empty() {
        postImageService.linkImagesToPost(42L, Set.of());

        verify(postImageRepository, never()).save(any());
        verify(imageRepository, never()).findByFileNames(anySet());
    }

    @Test
    @DisplayName("reconcile: 추가분 link + 제거분 중 미참조 이미지는 S3·DB 삭제")
    void reconcile_addsAndRemovesOrphan() {
        Image a = rawImage("a.png");
        Image b = rawImage("b.png");
        Image c = rawImage("c.png");
        when(postImageRepository.findByPostId(42L)).thenReturn(Set.of(new PostImage(42L, a), new PostImage(42L, b)));
        when(imageRepository.findByFileNames(Set.of("c.png"))).thenReturn(Set.of(c));
        when(postImageRepository.existsByImage(b)).thenReturn(false); // b no longer referenced

        postImageService.reconcilePostImages(42L, Set.of("a.png", "c.png"));

        verify(postImageRepository).save(any(PostImage.class)); // c linked
        verify(postImageRepository).deleteAllByPostImages(anySet());
        verify(minioService).deleteImage("b.png");
        verify(imageRepository).delete(b);
        verify(imageRepository, never()).delete(a); // a stays
    }

    @Test
    @DisplayName("reconcile: 제거된 이미지가 다른 post에서 여전히 참조되면 보존 (공유 이미지)")
    void reconcile_sharedImageNotDeleted() {
        Image shared = rawImage("shared.png");
        when(postImageRepository.findByPostId(42L)).thenReturn(Set.of(new PostImage(42L, shared)));
        when(postImageRepository.existsByImage(shared)).thenReturn(true); // another post still links it

        postImageService.reconcilePostImages(42L, Set.of()); // removed from this post

        verify(postImageRepository).deleteAllByPostImages(anySet());
        verify(minioService, never()).deleteImage(any());
        verify(imageRepository, never()).delete(any());
    }

    @Test
    @DisplayName("unlinkAllFromPost: 모든 link 제거 + 미참조 이미지 정리")
    void unlinkAllFromPost() {
        Image a = rawImage("a.png");
        when(postImageRepository.findByPostId(42L)).thenReturn(Set.of(new PostImage(42L, a)));
        when(postImageRepository.existsByImage(a)).thenReturn(false);

        postImageService.unlinkAllFromPost(42L);

        verify(postImageRepository).deleteAllByPostImages(anySet());
        verify(minioService).deleteImage("a.png");
        verify(imageRepository).delete(a);
    }

    @Test
    @DisplayName("cleanUpOrphanImages: orphan raw 삭제(+썸네일 동반), thumbnail row 자체는 제외")
    void cleanUpOrphanImages() {
        Image raw = rawImage("raw.png");
        Image thumb = new Image(raw, "http://oss/thumb", ZonedDateTime.now().plusHours(167));
        raw.setThumbNailImage(thumb);
        // query returns both raw (rawImage==null) and the thumbnail (rawImage!=null)
        when(imageRepository.findImagesEditedBefore(any())).thenReturn(Set.of(raw, thumb));

        int removed = postImageService.cleanUpOrphanImages(ZonedDateTime.now().minusHours(48));

        org.junit.jupiter.api.Assertions.assertEquals(1, removed); // only the raw counts
        verify(minioService).deleteThumbnail("raw.png");
        verify(imageRepository).delete(thumb);
        verify(minioService).deleteImage("raw.png");
        verify(imageRepository).delete(raw);
    }

    @Test
    @DisplayName("cleanUpOrphanImages: orphan 없음 → no-op")
    void cleanUpOrphanImages_empty() {
        when(imageRepository.findImagesEditedBefore(any())).thenReturn(Set.of());

        int removed = postImageService.cleanUpOrphanImages(ZonedDateTime.now().minusHours(48));

        org.junit.jupiter.api.Assertions.assertEquals(0, removed);
        verify(minioService, never()).deleteImage(any());
    }
}
