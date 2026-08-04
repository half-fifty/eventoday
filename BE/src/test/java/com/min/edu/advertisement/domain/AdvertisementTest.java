package com.min.edu.advertisement.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AdvertisementTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-08-03T12:00:00+09:00");

    @Test
    void 부스_광고를_승인하면_시작시각에_따라_예약상태가_된다() {
        Advertisement ad = ad(AdvertisementStatus.REVIEW_PENDING, now.plusDays(1));
        ad.approve(10L, now);
        assertEquals(AdvertisementStatus.SCHEDULED, ad.getStatus());
        assertEquals(10L, ad.getReviewedBy());
    }

    @Test
    void 이미_시작된_광고를_승인하면_활성상태가_된다() {
        Advertisement ad = ad(AdvertisementStatus.REVIEW_PENDING, now.minusHours(1));
        ad.approve(10L, now);
        assertEquals(AdvertisementStatus.ACTIVE, ad.getStatus());
    }

    @Test
    void 결제대기_행사광고는_승인할_수_없다() {
        Advertisement ad = ad(AdvertisementStatus.PAYMENT_PENDING, now.plusDays(1));
        assertThrows(IllegalStateException.class, () -> ad.approve(10L, now));
    }

    @Test
    void 종료시각이_지난_광고는_승인할_수_없다() {
        Advertisement ad = Advertisement.builder().id(1L).eventId(1L)
                .applicantOrganizationId(1L).startAt(now.minusDays(2)).endAt(now)
                .status(AdvertisementStatus.REVIEW_PENDING)
                .createdAt(now.minusDays(3)).updatedAt(now.minusDays(3)).build();

        assertThrows(IllegalStateException.class, () -> ad.approve(10L, now));
    }

    private Advertisement ad(AdvertisementStatus status, OffsetDateTime startAt) {
        return Advertisement.builder().id(1L).eventId(1L).applicantOrganizationId(1L)
                .startAt(startAt).endAt(now.plusDays(10)).status(status)
                .createdAt(now).updatedAt(now).build();
    }
}
