package com.multirkh.chimhahaimage.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Subset of the post.events.v1 payload this service cares about. PostCreated /
 * PostUpdated carry {@code imageFileNames}; PostDeleted carries only postId.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostEventPayload(
        String postId,
        List<String> imageFileNames
) {}
