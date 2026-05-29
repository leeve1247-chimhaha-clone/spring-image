package com.multirkh.chimhahaimage.image;

import com.multirkh.chimhahaimage.image.dtos.PresignedPostDto;
import com.multirkh.chimhahaimage.image.dtos.PresignedUrlDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ImageController {
    private final ImageService imageService;

    @GetMapping("/get/presigned-url")
    public PresignedUrlDTO getPresignedUrl() {
        return imageService.getPresignedUrl();
    }

    @GetMapping("/get/presigned-post")
    public PresignedPostDto getPresignedPost(
            @RequestHeader("X-File-MimeType") MimeType mimeType,
            @RequestHeader(value = "X-File-Sha256", required = false) String sha256
    ) {
        return imageService.getPresignedPost(mimeType, sha256);
    }

    @GetMapping("/get/src-url")
    public String getSrcUrl(@RequestParam("filename") String fileName) {
        return imageService.getSrcUrl(fileName);
    }

    @GetMapping("/get/thumbnail-src-url")
    public String getThumbnailSrcUrl(@RequestParam("filename") String fileName) {
        return imageService.getThumbnailSrcUrl(fileName);
    }
}
