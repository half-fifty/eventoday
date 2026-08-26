package com.min.edu.event.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EventTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-08-03T12:00:00+09:00");

    @Test
    void 행사_승인_공개_흐름을_처리한다() {
        Event event = event(EventStatus.PREPARING);

        event.submit(now);
        assertEquals(EventStatus.SUBMITTED, event.getStatus());
        event.approve(now.plusMinutes(1));
        assertEquals(EventStatus.APPROVED, event.getStatus());
        event.publish(now.plusMinutes(2));

        assertEquals(EventStatus.PUBLISHED, event.getStatus());
        assertEquals(now.plusMinutes(2), event.getPublishedAt());
    }

    @Test
    void 승인되지_않은_행사는_공개할_수_없다() {
        Event event = event(EventStatus.PREPARING);
        assertThrows(IllegalStateException.class, () -> event.publish(now));
    }

    @Test
    void 반려된_행사는_수정하고_재제출할_수_있다() {
        Event event = event(EventStatus.SUBMITTED);
        event.reject("정보 부족", now);

        event.update("수정 행사", "EXPO", "소개", "설명",
                EventDetailDisplayType.RICH_TEXT, null, "장소", "주소",
                null, null, null, null, null, null, null,
                now.plusDays(10), now.plusDays(11), now, now.plusDays(9),
                BigDecimal.ZERO, 100, 2, null, true, true, true, 10, now.plusMinutes(1));
        event.submit(now.plusMinutes(2));

        assertEquals(EventStatus.SUBMITTED, event.getStatus());
        assertEquals(null, event.getRejectionReason());
    }

    private Event event(EventStatus status) {
        return Event.builder().id(1L).organizerOrganizationId(1L).name("행사")
                .eventType("EXPO").description("설명")
                .detailDisplayType(EventDetailDisplayType.IMAGE_GALLERY)
                .venueName("장소").address("주소")
                .startAt(now.plusDays(10)).endAt(now.plusDays(11)).ticketPrice(BigDecimal.ZERO)
                .ticketTotalQuantity(100).ticketSoldQuantity(0).ticketPurchaseLimit(2)
                .status(status).noShowGraceMinutes(10).createdAt(now).updatedAt(now).build();
    }
}
