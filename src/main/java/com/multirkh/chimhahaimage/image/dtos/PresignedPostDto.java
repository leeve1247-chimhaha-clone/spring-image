package com.multirkh.chimhahaimage.image.dtos;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@Getter
public class PresignedPostDto {
    String url;
    Map<String, String> fields;
    boolean alreadyExists;

    public PresignedPostDto(
            String fileName,
            String url,
            Map<String, String> presignedPost
    ) {
        this.url = url;
        this.fields = presignedPost;
        this.fields.put("key", fileName);
        this.alreadyExists = false;
    }

    /**
     * Inline-dedup response: an image with the same SHA-256 already exists.
     * Client should skip the S3 upload and reuse {@code fields["key"]} as
     * the canonical fileName when assembling its src URL.
     */
    public static PresignedPostDto deduped(String fileName) {
        PresignedPostDto dto = new PresignedPostDto(fileName, "", new HashMap<>());
        dto.alreadyExists = true;
        return dto;
    }
}
