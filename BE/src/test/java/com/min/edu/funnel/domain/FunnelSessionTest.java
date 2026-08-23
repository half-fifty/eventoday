package com.min.edu.funnel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class FunnelSessionTest {

    private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-08-22T23:55:00+09:00");

    private FunnelSession session(FunnelStep maxStepReached, boolean dropped, boolean boothExplored,
            boolean stepSkipped, OffsetDateTime lastActionAt) {
        return FunnelSession.create(
                "session-1:42", 42L, "anon:visitor-1", maxStepReached,
                dropped, false, boothExplored, stepSkipped, STARTED_AT, lastActionAt, STARTED_AT);
    }

    @Test
    void mergeLaterActions_higherStepArrives_advancesMaxStepAndClearsDropped() {
        FunnelSession existing = session(FunnelStep.OPEN_PURCHASE_MODAL, true, false, false, STARTED_AT);
        OffsetDateTime laterAt = STARTED_AT.plusMinutes(20);

        existing.mergeLaterActions(FunnelStep.COMPLETE_PAYMENT, false, false, laterAt, laterAt);

        assertThat(existing.getMaxStepReached()).isEqualTo(FunnelStep.COMPLETE_PAYMENT);
        assertThat(existing.isDropped()).isFalse();
        assertThat(existing.getLastActionAt()).isEqualTo(laterAt);
    }

    @Test
    void mergeLaterActions_lowerStepArrives_doesNotRegressMaxStep() {
        FunnelSession existing = session(FunnelStep.COMPLETE_PAYMENT, false, false, false, STARTED_AT);
        OffsetDateTime laterAt = STARTED_AT.plusMinutes(5);

        existing.mergeLaterActions(FunnelStep.VIEW_EVENT_DETAIL, false, false, laterAt, laterAt);

        assertThat(existing.getMaxStepReached()).isEqualTo(FunnelStep.COMPLETE_PAYMENT);
        assertThat(existing.isDropped()).isFalse();
    }

    @Test
    void mergeLaterActions_flagsAccumulateWithOrAndNeverRegress() {
        FunnelSession existing = session(FunnelStep.VIEW_EVENT_DETAIL, true, true, false, STARTED_AT);
        OffsetDateTime laterAt = STARTED_AT.plusMinutes(10);

        existing.mergeLaterActions(FunnelStep.OPEN_PURCHASE_MODAL, false, true, laterAt, laterAt);

        assertThat(existing.isBoothExplored()).isTrue();
        assertThat(existing.isStepSkipped()).isTrue();
    }

    @Test
    void mergeLaterActions_earlierLastActionAt_doesNotRegressLastActionAt() {
        OffsetDateTime existingLastActionAt = STARTED_AT.plusMinutes(15);
        FunnelSession existing = session(FunnelStep.OPEN_PURCHASE_MODAL, true, false, false, existingLastActionAt);

        existing.mergeLaterActions(
                FunnelStep.VIEW_EVENT_DETAIL, false, false, STARTED_AT.plusMinutes(1), STARTED_AT.plusMinutes(1));

        assertThat(existing.getLastActionAt()).isEqualTo(existingLastActionAt);
    }
}
