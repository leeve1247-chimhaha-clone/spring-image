package com.multirkh.chimhahaimage.cleanup;

import com.multirkh.chimhahaimage.image.PostImageService;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reclaims raw images that were uploaded but never attached to a post within
 * the grace window. Images dropped from a post are reclaimed immediately by
 * {@link PostImageService}; this is the safety net for never-attached uploads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrphanImageCleanupTask {
    static final long ORPHAN_IMAGE_GRACE_HOURS = 48;

    private final PostImageService postImageService;

    @Scheduled(cron = "0 0 1 * * *")
    public void cleanUpOrphanImages() {
        int removed = postImageService.cleanUpOrphanImages(
                ZonedDateTime.now().minusHours(ORPHAN_IMAGE_GRACE_HOURS));
        if (removed > 0) {
            log.info("[CLEANUP] removed {} orphan image(s)", removed);
        }
    }
}
