package com.min.edu.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

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
