package com.multirkh.chimhahaimage.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multirkh.chimhahaimage.image.PostImageService;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostEventConsumerTest {

    @Mock
    private PostImageService postImageService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PostEventConsumer consumer;

    private ConsumerRecord<String, String> record(String eventId, String eventType, String value) {
        ConsumerRecord<String, String> r = new ConsumerRecord<>("post.events.v1", 0, 0L, "42", value);
        if (eventId != null) {
            r.headers().add(PostEventConsumer.HEADER_EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
        }
        if (eventType != null) {
            r.headers().add(PostEventConsumer.HEADER_EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8));
        }
        return r;
    }

    private static String payloadJson() {
        return "{\"postId\":\"42\",\"imageFileNames\":[\"a.png\",\"b.png\"]}";
    }

    @Test
    @DisplayName("PostCreated → linkImagesToPost(postId, fileNames) + ProcessedEvent saved")
    void postCreatedLinksImages() throws Exception {
        when(processedEventRepository.existsById("e1")).thenReturn(false);

        consumer.onMessage(record("e1", "PostCreated", payloadJson()));

        verify(postImageService).linkImagesToPost(42L, Set.of("a.png", "b.png"));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("PostUpdated → reconcilePostImages(postId, fileNames)")
    void postUpdatedReconciles() throws Exception {
        when(processedEventRepository.existsById("e2")).thenReturn(false);

        consumer.onMessage(record("e2", "PostUpdated", payloadJson()));

        verify(postImageService).reconcilePostImages(42L, Set.of("a.png", "b.png"));
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("PostDeleted → unlinkAllFromPost(postId)")
    void postDeletedUnlinks() throws Exception {
        when(processedEventRepository.existsById("e3")).thenReturn(false);

        consumer.onMessage(record("e3", "PostDeleted", "{\"postId\":\"42\"}"));

        verify(postImageService).unlinkAllFromPost(42L);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("중복 event-id → existsById true → 서비스 호출 0 + save 0")
    void duplicateEventIdSkips() throws Exception {
        when(processedEventRepository.existsById("e1")).thenReturn(true);

        consumer.onMessage(record("e1", "PostCreated", payloadJson()));

        verify(processedEventRepository, never()).save(any());
        verifyNoInteractions(postImageService);
    }

    @Test
    @DisplayName("event-id header 없음 → skip, DB/서비스 접근 0")
    void missingEventIdHeader() throws Exception {
        consumer.onMessage(record(null, "PostCreated", payloadJson()));

        verifyNoInteractions(processedEventRepository);
        verifyNoInteractions(postImageService);
    }

    @Test
    @DisplayName("알 수 없는 event-type → skip, 서비스 호출 0")
    void unknownEventTypeIgnored() throws Exception {
        consumer.onMessage(record("e9", "CommentCreated", payloadJson()));

        verifyNoInteractions(postImageService);
        verify(processedEventRepository, never()).save(any());
    }
}
