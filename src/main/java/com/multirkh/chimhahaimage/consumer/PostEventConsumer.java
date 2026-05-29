package com.multirkh.chimhahaimage.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multirkh.chimhahaimage.image.PostImageService;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes post.events.v1 and keeps the post ↔ image association in sync.
 * Idempotent via processed_event(event_id PK) + existsById guard, mirroring
 * the notification service's consumer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostEventConsumer {

    public static final String CONSUMER_GROUP = "chimhaha-image";
    public static final String HEADER_EVENT_ID = "event-id";
    public static final String HEADER_EVENT_TYPE = "event-type";

    private final PostImageService postImageService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "post.events.v1", groupId = CONSUMER_GROUP)
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record) throws Exception {
        String eventId = headerString(record, HEADER_EVENT_ID);
        String eventType = headerString(record, HEADER_EVENT_TYPE);

        if (eventId == null) {
            log.warn("[image-consumer] missing event-id header, skipping. topic={} offset={}",
                    record.topic(), record.offset());
            return;
        }
        if (!isHandled(eventType)) {
            log.debug("[image-consumer] ignoring eventType={}", eventType);
            return;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.info("[image-consumer] duplicate event-id={}, skipping", eventId);
            return;
        }

        PostEventPayload payload = objectMapper.readValue(record.value(), PostEventPayload.class);
        Long postId = Long.valueOf(payload.postId());
        switch (eventType) {
            case "PostCreated" -> postImageService.linkImagesToPost(postId, toSet(payload.imageFileNames()));
            case "PostUpdated" -> postImageService.reconcilePostImages(postId, toSet(payload.imageFileNames()));
            case "PostDeleted" -> postImageService.unlinkAllFromPost(postId);
            default -> { /* unreachable — guarded by isHandled */ }
        }
        processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER_GROUP));
        log.info("[image-consumer] handled eventType={} postId={} event-id={}", eventType, postId, eventId);
    }

    private static boolean isHandled(String eventType) {
        return "PostCreated".equals(eventType)
                || "PostUpdated".equals(eventType)
                || "PostDeleted".equals(eventType);
    }

    private static Set<String> toSet(java.util.List<String> fileNames) {
        return fileNames == null ? Set.of() : new HashSet<>(fileNames);
    }

    private static String headerString(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
