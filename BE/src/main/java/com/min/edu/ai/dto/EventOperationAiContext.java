package com.min.edu.ai.dto;

import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import java.time.OffsetDateTime;

public record EventOperationAiContext(
        Long eventId,
        String eventName,
        EventStatus status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String venueName) {

    public static EventOperationAiContext from(Event event) {
        return new EventOperationAiContext(
            event.getId(),
            event.getName(),
            event.getStatus(),
            event.getStartAt(),
            event.getEndAt(),
            event.getVenueName()
        );
    }
}
