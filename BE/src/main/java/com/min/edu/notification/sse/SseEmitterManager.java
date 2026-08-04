package com.min.edu.notification.sse;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.min.edu.notification.dto.NotificationResponseDto;

@Component
public class SseEmitterManager {
    private static final long TIMEOUT_MILLIS = 60L * 60 * 1000; // 1시간
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);

        emitters.computeIfAbsent(
                memberId,
                key -> ConcurrentHashMap.newKeySet()).add(emitter);

        emitter.onCompletion(
                () -> remove(memberId, emitter));

        emitter.onTimeout(() -> {
            remove(memberId, emitter);
            emitter.complete();
        });

        emitter.onError(
                error -> remove(memberId, emitter));

        try {
            emitter.send(
                    SseEmitter.event()
                            .name("connect")
                            .data("connected"));
        } catch (IOException exception) {
            remove(memberId, emitter);
        }

        return emitter;
    }

    public void send(
            Long memberId,
            NotificationResponseDto notification) {
        Set<SseEmitter> memberEmitters = emitters.get(memberId);

        if (memberEmitters == null
                || memberEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : memberEmitters) {
            sendToEmitter(
                    memberId,
                    emitter,
                    notification);
        }
    }

    private void remove(
            Long memberId,
            SseEmitter emitter) {
        emitters.computeIfPresent(
                memberId,
                (key, memberEmitters) -> {
                    memberEmitters.remove(emitter);

                    if (memberEmitters.isEmpty()) {
                        return null;
                    }

                    return memberEmitters;
                });
    }

    private void sendToEmitter(
            Long memberId,
            SseEmitter emitter,
            NotificationResponseDto notification) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .id(notification.id().toString())
                            .name("notification")
                            .data(notification));
        } catch (IOException | IllegalStateException exception) {
            remove(memberId, emitter);
        }
    }
}
