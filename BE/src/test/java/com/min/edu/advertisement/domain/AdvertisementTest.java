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
    void 행사광고에_결제주문을_연결하고_결제완료로_전환한다() {
        Advertisement ad = ad(AdvertisementStatus.PAYMENT_PENDING, now.plusDays(1));

        ad.assignPaymentOrder(99L, now);
        ad.markPaid(now.plusMinutes(1));

        assertEquals(99L, ad.getPaymentOrderId());
        assertEquals(AdvertisementStatus.PAID, ad.getStatus());
    }

    @Test
    void 결제주문이_없는_광고는_결제완료로_전환할_수_없다() {
        Advertisement ad = ad(AdvertisementStatus.PAYMENT_PENDING, now.plusDays(1));
        assertThrows(IllegalStateException.class, () -> ad.markPaid(now));
    }

    @Test
    void 종료시각이_지난_광고는_승인할_수_없다() {
        Advertisement ad = Advertisement.builder().id(1L).eventId(1L)
                .applicantOrganizationId(1L).startAt(now.minusDays(2)).endAt(now)
                .status(AdvertisementStatus.REVIEW_PENDING)
                .createdAt(now.minusDays(3)).updatedAt(now.minusDays(3)).build();

        assertThrows(IllegalStateException.class, () -> ad.approve(10L, now));
    }

    @Test
    void 노출중인_광고의_문구와_이미지를_수정하면_재심사상태가_된다() {
        Advertisement ad = ad(AdvertisementStatus.ACTIVE, now.minusHours(1));

        ad.updateCreative(15L, "새 광고 문구", now);

        assertEquals(15L, ad.getBannerFileId());
        assertEquals("새 광고 문구", ad.getAdText());
        assertEquals(AdvertisementStatus.REVISION_PENDING, ad.getStatus());
        assertEquals(null, ad.getReviewedBy());
        assertEquals(null, ad.getApprovedAt());
    }

    @Test
    void 노출예정_광고의_콘텐츠를_수정하면_재심사상태가_된다() {
        Advertisement ad = ad(AdvertisementStatus.SCHEDULED, now.plusDays(1));

        ad.updateCreative(15L, "수정 광고 문구", now);

        assertEquals(AdvertisementStatus.REVISION_PENDING, ad.getStatus());
    }

    @Test
    void 이미_노출된_수정재심사_광고는_환불없이_중단할_수_있다() {
        Advertisement ad = ad(AdvertisementStatus.ACTIVE, now.minusHours(1));
        ad.updateCreative(15L, "수정 광고 문구", now);

        ad.stopRevision(now.plusMinutes(1));

        assertEquals(AdvertisementStatus.STOPPED, ad.getStatus());
    }

    @Test
    void 심사중인_광고는_노출콘텐츠_전용수정을_할_수_없다() {
        Advertisement ad = ad(AdvertisementStatus.REVIEW_PENDING, now.plusDays(1));
        assertThrows(IllegalStateException.class,
                () -> ad.updateCreative(15L, "새 광고 문구", now));
    }

    @Test
    void scheduledAdvertisementBecomesRefundedAfterFullRefund() {
        Advertisement ad = ad(AdvertisementStatus.SCHEDULED, now.plusDays(1));
        ad.markRefunded(now);
        assertEquals(AdvertisementStatus.REFUNDED, ad.getStatus());
    }

    @Test
    void activeAdvertisementCanStopButCannotRefund() {
        Advertisement ad = ad(AdvertisementStatus.ACTIVE, now.minusHours(1));
        assertThrows(IllegalStateException.class, () -> ad.markRefunded(now));
        ad.stop(now);
        assertEquals(AdvertisementStatus.STOPPED, ad.getStatus());
    }

    @Test
    void terminalAdvertisementCanBeArchived() {
        Advertisement ad = ad(AdvertisementStatus.REFUNDED, now.minusDays(1));
        ad.archive(now);
        assertEquals(now, ad.getArchivedAt());
    }

    @Test
    void activeAdvertisementCannotBeArchived() {
        Advertisement ad = ad(AdvertisementStatus.ACTIVE, now.minusHours(1));
        assertThrows(IllegalStateException.class, () -> ad.archive(now));
    }

    private Advertisement ad(AdvertisementStatus status, OffsetDateTime startAt) {
        return Advertisement.builder().id(1L).eventId(1L).applicantOrganizationId(1L)
                .startAt(startAt).endAt(now.plusDays(10)).status(status)
                .createdAt(now).updatedAt(now).build();
    }
}
