package com.min.edu.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EventPosterUpdateTest {

    @Test
    void updateRepresentativeFile_allowsPublishedEvent() {
        Event event = event(EventStatus.PUBLISHED, 10L);
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-08-08T10:00:00+09:00");

        event.updateRepresentativeFile(20L, updatedAt);

        assertThat(event.getRepresentativeFileId()).isEqualTo(20L);
        assertThat(event.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void updateRepresentativeFile_rejectsCancelledEvent() {
        Event event = event(EventStatus.CANCELLED, 10L);

        assertThatThrownBy(() -> event.updateRepresentativeFile(20L, OffsetDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("취소된 행사");
        assertThat(event.getRepresentativeFileId()).isEqualTo(10L);
    }

    private Event event(EventStatus status, Long representativeFileId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-01T10:00:00+09:00");
        return Event.builder()
                .id(1L)
                .organizerOrganizationId(2L)
                .name("테스트 행사")
                .eventType("EXPO")
                .description("테스트")
                .venueName("테스트 전시장")
                .address("서울특별시")
                .startAt(now.plusDays(1))
                .endAt(now.plusDays(2))
                .ticketPrice(java.math.BigDecimal.ZERO)
                .ticketTotalQuantity(100)
                .ticketSoldQuantity(0)
                .ticketPurchaseLimit(1)
                .representativeFileId(representativeFileId)
                .status(status)
                .noShowGraceMinutes(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
