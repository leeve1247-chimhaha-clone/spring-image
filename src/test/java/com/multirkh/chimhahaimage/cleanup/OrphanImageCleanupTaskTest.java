package com.multirkh.chimhahaimage.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.multirkh.chimhahaimage.image.PostImageService;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrphanImageCleanupTaskTest {

    @Mock
    private PostImageService postImageService;

    @InjectMocks
    private OrphanImageCleanupTask task;

    @Test
    @DisplayName("cleanUpOrphanImages: 48시간 유예 임계로 PostImageService에 정리를 위임한다")
    void delegatesWithGraceThreshold() {
        when(postImageService.cleanUpOrphanImages(any())).thenReturn(3);

        ZonedDateTime before = ZonedDateTime.now().minusHours(OrphanImageCleanupTask.ORPHAN_IMAGE_GRACE_HOURS);
        task.cleanUpOrphanImages();
        ZonedDateTime after = ZonedDateTime.now().minusHours(OrphanImageCleanupTask.ORPHAN_IMAGE_GRACE_HOURS);

        ArgumentCaptor<ZonedDateTime> captor = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(postImageService).cleanUpOrphanImages(captor.capture());
        assertThat(captor.getValue()).isBetween(before, after);
    }
}
