package com.multirkh.chimhahaimage.image;

import com.multirkh.chimhahaimage.common.minio.MinioService;
import com.multirkh.chimhahaimage.image.domain.Image;
import com.multirkh.chimhahaimage.image.domain.PostImage;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the post ↔ image association driven by post.events.v1. The post itself
 * lives in the monolith; here we only track which images a post references
 * (PostImage) and reclaim images that no post references anymore.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PostImageService {

    private final ImageRepository imageRepository;
    private final PostImageRepository postImageRepository;
    private final MinioService minioService;

    /** PostCreated: link every referenced image that exists. Unknown filenames are skipped. */
    public void linkImagesToPost(Long postId, Set<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) {
            return;
        }
        Set<Image> images = imageRepository.findByFileNames(fileNames);
        for (Image image : images) {
            postImageRepository.save(new PostImage(postId, image));
        }
    }

    /** PostUpdated: make the post's links match {@code fileNames}; reclaim images dropped from it. */
    public void reconcilePostImages(Long postId, Set<String> fileNames) {
        Set<String> desired = fileNames == null ? Set.of() : fileNames;
        Set<PostImage> existing = postImageRepository.findByPostId(postId);
        Set<String> existingNames = existing.stream()
                .map(pi -> pi.getImage().getFileName())
                .collect(Collectors.toSet());

        Set<String> toAdd = new HashSet<>(desired);
        toAdd.removeAll(existingNames);
        linkImagesToPost(postId, toAdd);

        Set<PostImage> toRemove = existing.stream()
                .filter(pi -> !desired.contains(pi.getImage().getFileName()))
                .collect(Collectors.toSet());
        removeLinksAndCleanup(toRemove);
    }

    /** PostDeleted: drop all the post's links and reclaim any now-orphaned images. */
    public void unlinkAllFromPost(Long postId) {
        removeLinksAndCleanup(postImageRepository.findByPostId(postId));
    }

    /**
     * Scheduled sweep for uploads that were never attached to a post.
     * Returns the number of raw images removed.
     */
    public int cleanUpOrphanImages(ZonedDateTime threshold) {
        Set<Image> orphans = imageRepository.findImagesEditedBefore(threshold).stream()
                .filter(image -> image.getRawImage() == null)
                .collect(Collectors.toSet());
        for (Image orphan : orphans) {
            deleteImageWithThumbnail(orphan);
        }
        return orphans.size();
    }

    private void removeLinksAndCleanup(Set<PostImage> links) {
        if (links.isEmpty()) {
            return;
        }
        Set<Image> candidates = links.stream().map(PostImage::getImage).collect(Collectors.toSet());
        postImageRepository.deleteAllByPostImages(links);
        for (Image image : candidates) {
            if (!postImageRepository.existsByImage(image)) {
                deleteImageWithThumbnail(image);
            }
        }
    }

    private void deleteImageWithThumbnail(Image image) {
        Image thumbNail = image.getThumbNailImage();
        if (thumbNail != null) {
            minioService.deleteThumbnail(image.getFileName());
            imageRepository.delete(thumbNail); // child row (FK -> raw image) must go first
        }
        minioService.deleteImage(image.getFileName());
        imageRepository.delete(image);
    }
}
