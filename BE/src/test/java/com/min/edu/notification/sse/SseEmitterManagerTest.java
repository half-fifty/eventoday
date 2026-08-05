package com.min.edu.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.min.edu.notification.domain.NotificationType;
import com.min.edu.notification.dto.NotificationResponseDto;

class SseEmitterManagerTest {

    private SseEmitterManager sseEmitterManager;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sseEmitterManager = new SseEmitterManager();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestSseController(sseEmitterManager))
                .build();
    }

    @Test
    void connect_sendsInitialConnectEvent() throws Exception {
        MvcResult result = mockMvc.perform(get("/test-stream/1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("event:connect")
                .contains("data:connected");
    }

    @Test
    void send_deliversNotificationOnlyToConnectedMember() throws Exception {
        MvcResult memberOneResult = mockMvc.perform(get("/test-stream/1"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult memberTwoResult = mockMvc.perform(get("/test-stream/2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        sseEmitterManager.send(1L, notificationResponse());

        assertThat(memberOneResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("id:7")
                .contains("event:notification")
                .contains("일정 변경");
        assertThat(memberTwoResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("event:notification")
                .doesNotContain("일정 변경");
    }

    @Test
    void send_doesNothingWhenMemberHasNoConnection() {
        assertThatCode(() -> sseEmitterManager.send(999L, notificationResponse()))
                .doesNotThrowAnyException();
    }

    @Test
    void send_continuesToOtherConnectionsWhenRuntimeExceptionOccurs() throws Exception {
        Long memberId = 1L;
        RuntimeExceptionEmitter failedEmitter = new RuntimeExceptionEmitter();
        RecordingEmitter healthyEmitter = new RecordingEmitter();
        Set<SseEmitter> memberEmitters = new CopyOnWriteArraySet<>();
        memberEmitters.add(failedEmitter);
        memberEmitters.add(healthyEmitter);
        emitterMap().put(memberId, memberEmitters);

        assertThatCode(() -> sseEmitterManager.send(memberId, notificationResponse()))
                .doesNotThrowAnyException();

        assertThat(memberEmitters)
                .doesNotContain(failedEmitter)
                .contains(healthyEmitter);
        assertThat(healthyEmitter.isNotificationSent()).isTrue();
    }

    @Test
    void connect_keepsNewEmitterWhenOldEmitterIsRemovedConcurrently() throws Exception {
        Long memberId = 1L;
        SseEmitter oldEmitter = new SseEmitter();
        BlockingAddSet blockingSet = new BlockingAddSet(oldEmitter);
        Map<Long, Set<SseEmitter>> emitterMap = emitterMap();
        emitterMap.put(memberId, blockingSet);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch removalAttempted = new CountDownLatch(1);

        try {
            Future<SseEmitter> connectFuture = executor.submit(
                    () -> sseEmitterManager.connect(memberId));
            assertThat(blockingSet.awaitAddStarted()).isTrue();

            Future<?> removeFuture = executor.submit(() -> {
                removalAttempted.countDown();
                invokeRemove(memberId, oldEmitter);
            });
            assertThat(removalAttempted.await(1, TimeUnit.SECONDS)).isTrue();
            waitForRemovalIfItIsNotBlocked(removeFuture);

            blockingSet.allowAdd();
            SseEmitter newEmitter = connectFuture.get(2, TimeUnit.SECONDS);
            removeFuture.get(2, TimeUnit.SECONDS);

            assertThat(emitterMap.get(memberId))
                    .as("살아 있는 새 SSE 연결은 Map에 남아 있어야 합니다.")
                    .isNotNull()
                    .contains(newEmitter);
        } finally {
            blockingSet.allowAdd();
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Set<SseEmitter>> emitterMap() throws Exception {
        Field field = SseEmitterManager.class.getDeclaredField("emitters");
        field.setAccessible(true);
        return (Map<Long, Set<SseEmitter>>) field.get(sseEmitterManager);
    }

    private void invokeRemove(Long memberId, SseEmitter emitter) {
        try {
            Method method = SseEmitterManager.class.getDeclaredMethod(
                    "remove",
                    Long.class,
                    SseEmitter.class);
            method.setAccessible(true);
            method.invoke(sseEmitterManager, memberId, emitter);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void waitForRemovalIfItIsNotBlocked(Future<?> removeFuture) throws Exception {
        try {
            removeFuture.get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ignored) {
            // compute() 수정 후에는 연결 추가가 완료될 때까지 제거가 대기한다.
        }
    }

    private NotificationResponseDto notificationResponse() {
        return new NotificationResponseDto(
                7L,
                NotificationType.EVENT_SCHEDULE_CHANGED,
                "EVENT",
                10L,
                "일정 변경",
                "행사 일정이 변경되었습니다.",
                false,
                null,
                OffsetDateTime.parse("2026-08-05T10:00:00+09:00"));
    }

    private static class BlockingAddSet extends AbstractSet<SseEmitter> {

        private final Set<SseEmitter> delegate = ConcurrentHashMap.newKeySet();
        private final CountDownLatch addStarted = new CountDownLatch(1);
        private final CountDownLatch addAllowed = new CountDownLatch(1);

        private BlockingAddSet(SseEmitter initialEmitter) {
            delegate.add(initialEmitter);
        }

        @Override
        public boolean add(SseEmitter emitter) {
            addStarted.countDown();
            try {
                if (!addAllowed.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("SSE 연결 추가 대기 시간을 초과했습니다.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return delegate.add(emitter);
        }

        @Override
        public boolean remove(Object emitter) {
            return delegate.remove(emitter);
        }

        @Override
        public Iterator<SseEmitter> iterator() {
            return delegate.iterator();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        private boolean awaitAddStarted() throws InterruptedException {
            return addStarted.await(1, TimeUnit.SECONDS);
        }

        private void allowAdd() {
            addAllowed.countDown();
        }
    }

    private static class RuntimeExceptionEmitter extends SseEmitter {

        @Override
        public void send(SseEventBuilder builder) {
            throw new RuntimeException("종료된 비동기 SSE 연결입니다.");
        }
    }

    private static class RecordingEmitter extends SseEmitter {

        private boolean notificationSent;

        @Override
        public void send(SseEventBuilder builder) {
            notificationSent = true;
        }

        private boolean isNotificationSent() {
            return notificationSent;
        }
    }

    @RestController
    static class TestSseController {

        private final SseEmitterManager sseEmitterManager;

        private TestSseController(SseEmitterManager sseEmitterManager) {
            this.sseEmitterManager = sseEmitterManager;
        }

        @GetMapping(value = "/test-stream/{memberId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public SseEmitter stream(@PathVariable Long memberId) {
            return sseEmitterManager.connect(memberId);
        }
    }
}
