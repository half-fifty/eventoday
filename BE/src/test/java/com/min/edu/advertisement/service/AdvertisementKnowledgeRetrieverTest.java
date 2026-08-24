package com.min.edu.advertisement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AdvertisementKnowledgeRetrieverTest {

    private final AdvertisementKnowledgeRetriever retriever = new AdvertisementKnowledgeRetriever();

    @Test
    void 행사정보와_필수광고정책을_검색근거로_반환한다() {
        Event event = event();

        AdvertisementKnowledgeRetriever.RetrievalResult result =
                retriever.retrieve(event, "코엑스 무료 전시", "짧고 강렬하게");

        assertThat(result.sources())
                .contains("광고 정책 · 사실성", "광고 정책 · 과장 표현", "행사 장소", "관람 가격");
        assertThat(result.asPromptContext())
                .contains("서울 디자인 페어", "코엑스", "무료 입장")
                .doesNotContain("<strong>");
        assertThat(result.documents()).hasSizeLessThanOrEqualTo(6);
    }

    @Test
    void 검색어가_부족해도_사실성과_과장표현_정책은_항상_포함한다() {
        AdvertisementKnowledgeRetriever.RetrievalResult result = retriever.retrieve(event(), null, null);

        assertThat(result.sources()).contains("광고 정책 · 사실성", "광고 정책 · 과장 표현");
    }

    private Event event() {
        OffsetDateTime start = OffsetDateTime.parse("2026-09-01T10:00:00+09:00");
        return Event.builder()
                .id(1L)
                .organizerOrganizationId(2L)
                .name("서울 디자인 페어")
                .eventType("EXPO")
                .status(EventStatus.PREPARING)
                .shortDescription("디자인 브랜드 전시")
                .description("<p><strong>신진 디자이너</strong>의 작품을 소개합니다.</p>")
                .venueName("코엑스")
                .address("서울특별시 강남구 영동대로 513")
                .ticketPrice(BigDecimal.ZERO)
                .startAt(start)
                .endAt(start.plusDays(2))
                .createdAt(start.minusMonths(1))
                .updatedAt(start.minusMonths(1))
                .build();
    }
}
