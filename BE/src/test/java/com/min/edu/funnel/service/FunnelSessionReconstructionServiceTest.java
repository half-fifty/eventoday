package com.min.edu.funnel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.funnel.domain.FunnelAction;
import com.min.edu.funnel.domain.FunnelSession;
import com.min.edu.funnel.domain.FunnelStep;
import com.min.edu.funnel.domain.VisitorProfile;
import com.min.edu.funnel.dto.FunnelActionEventDto;
import com.min.edu.funnel.repository.FunnelActionRepository;
import com.min.edu.funnel.repository.FunnelSessionRepository;
import com.min.edu.funnel.repository.VisitorProfileRepository;
import com.min.edu.member.domain.PlatformRole;

@ExtendWith(MockitoExtension.class)
class FunnelSessionReconstructionServiceTest {

    private static final Long EVENT_ID = 42L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 17);

    @Mock
    private FunnelActionRepository funnelActionRepository;

    @Mock
    private FunnelSessionRepository funnelSessionRepository;

    @Mock
    private VisitorProfileRepository visitorProfileRepository;

    private FunnelSessionReconstructionService service;

    @BeforeEach
    void setUp() {
        service = new FunnelSessionReconstructionService(
                funnelActionRepository, funnelSessionRepository, visitorProfileRepository);
        org.mockito.Mockito.lenient()
                .when(funnelSessionRepository.findBySessionId(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
    }

    private FunnelAction action(String sessionId, String actionType, OffsetDateTime receivedAt) {
        return action(sessionId, actionType, receivedAt, "anon-1", null);
    }

    private FunnelAction action(
            String sessionId, String actionType, OffsetDateTime receivedAt, String anonymousId, Long userId) {
        return FunnelAction.from(new FunnelActionEventDto(
                UUID.randomUUID().toString(),
                sessionId,
                EVENT_ID,
                anonymousId,
                userId,
                actionType,
                receivedAt,
                receivedAt,
                Map.of()));
    }

    // 실제 ES 저장소는 session_id 정렬 기반 keyset scroll(Window)로 페이지 단위 응답을 준다
    // (FunnelActionRepository 참고). 테스트에서는 단일 페이지(hasNext=false)로 전체 액션을 담아
    // 돌려주는 것으로 충분하다 — 스트리밍 집계 로직 자체는 페이지 수와 무관하게 동작한다.
    private void mockActions(Long eventId, List<FunnelAction> actions) {
        given(funnelActionRepository.findFirst500ByEventIdAndReceivedAtBetweenOrderBySessionIdAscActionIdAsc(
                eq(eventId), any(), any(), any()))
                .willReturn(Window.from(actions, index -> ScrollPosition.offset(index)));
    }

    private FunnelSession runAndCaptureSavedSession(List<FunnelAction> actions) {
        mockActions(EVENT_ID, actions);
        given(visitorProfileRepository.findByVisitorKey(org.mockito.ArgumentMatchers.anyString()))
                .willReturn(Optional.empty());

        service.reconstruct(EVENT_ID, TARGET_DATE);

        ArgumentCaptor<FunnelSession> captor = ArgumentCaptor.forClass(FunnelSession.class);
        verify(funnelSessionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void reconstruct_sessionReachedPayment_marksNotDroppedWithMaxStepPayment() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        List<FunnelAction> actions = List.of(
                action("session-1", "VIEW_EVENT_DETAIL", t0),
                action("session-1", "OPEN_PURCHASE_MODAL", t0.plusMinutes(1)),
                action("session-1", "COMPLETE_PAYMENT", t0.plusMinutes(2)));

        FunnelSession saved = runAndCaptureSavedSession(actions);

        assertThat(saved.getMaxStepReached()).isEqualTo(FunnelStep.COMPLETE_PAYMENT);
        assertThat(saved.isDropped()).isFalse();
        assertThat(saved.getStartedAt()).isEqualTo(t0);
        assertThat(saved.getLastActionAt()).isEqualTo(t0.plusMinutes(2));
    }

    @Test
    void reconstruct_sessionStoppedAtPurchaseModal_marksDropped() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        List<FunnelAction> actions = List.of(
                action("session-2", "VIEW_EVENT_DETAIL", t0),
                action("session-2", "OPEN_PURCHASE_MODAL", t0.plusMinutes(1)));

        FunnelSession saved = runAndCaptureSavedSession(actions);

        assertThat(saved.getMaxStepReached()).isEqualTo(FunnelStep.OPEN_PURCHASE_MODAL);
        assertThat(saved.isDropped()).isTrue();
    }

    @Test
    void reconstruct_sessionViewedBoothList_marksBoothExplored() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        List<FunnelAction> actions = List.of(
                action("session-3", "VIEW_EVENT_DETAIL", t0),
                action("session-3", "VIEW_BOOTH_LIST", t0.plusMinutes(1)));

        FunnelSession saved = runAndCaptureSavedSession(actions);

        assertThat(saved.isBoothExplored()).isTrue();
    }

    @Test
    void reconstruct_boothListNotVisited_marksBoothNotExplored() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        List<FunnelAction> actions = List.of(action("session-4", "VIEW_EVENT_DETAIL", t0));

        FunnelSession saved = runAndCaptureSavedSession(actions);

        assertThat(saved.isBoothExplored()).isFalse();
    }

    @Test
    void reconstruct_openedModalWithoutViewingEventDetail_marksStepSkipped() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        List<FunnelAction> actions = List.of(action("session-5", "OPEN_PURCHASE_MODAL", t0));

        FunnelSession saved = runAndCaptureSavedSession(actions);

        assertThat(saved.isStepSkipped()).isTrue();
    }

    @Test
    void reconstruct_normalStepOrder_doesNotMarkStepSkipped() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        List<FunnelAction> actions = List.of(
                action("session-6", "VIEW_EVENT_DETAIL", t0),
                action("session-6", "OPEN_PURCHASE_MODAL", t0.plusMinutes(1)));

        FunnelSession saved = runAndCaptureSavedSession(actions);

        assertThat(saved.isStepSkipped()).isFalse();
    }

    @Test
    void reconstruct_noExistingVisitorProfile_marksNewVisitorAndCreatesProfile() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        List<FunnelAction> actions = List.of(action("session-7", "VIEW_EVENT_DETAIL", t0));
        mockActions(EVENT_ID, actions);
        given(visitorProfileRepository.findByVisitorKey("anon:anon-1")).willReturn(Optional.empty());

        service.reconstruct(EVENT_ID, TARGET_DATE);

        ArgumentCaptor<FunnelSession> sessionCaptor = ArgumentCaptor.forClass(FunnelSession.class);
        verify(funnelSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().isReturningVisitor()).isFalse();
        verify(visitorProfileRepository).save(org.mockito.ArgumentMatchers.any(VisitorProfile.class));
    }

    @Test
    void reconstruct_existingVisitorProfile_marksReturningVisitorAndDoesNotCreateNewProfile() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        VisitorProfile existingProfile = VisitorProfile.create(
                "anon:anon-1", "anon-1", null, OffsetDateTime.parse("2026-08-01T00:00:00+09:00"));
        List<FunnelAction> actions = List.of(action("session-8", "VIEW_EVENT_DETAIL", t0));
        mockActions(EVENT_ID, actions);
        given(visitorProfileRepository.findByVisitorKey("anon:anon-1")).willReturn(Optional.of(existingProfile));

        service.reconstruct(EVENT_ID, TARGET_DATE);

        ArgumentCaptor<FunnelSession> sessionCaptor = ArgumentCaptor.forClass(FunnelSession.class);
        verify(funnelSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().isReturningVisitor()).isTrue();
        assertThat(existingProfile.getLastSeenAt()).isEqualTo(t0);
        verify(visitorProfileRepository, never()).save(any(VisitorProfile.class));
    }

    @Test
    void reconstruct_sessionAlreadyProcessed_mergesIntoExistingInsteadOfCreatingNew() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        List<FunnelAction> actions = List.of(action("session-9", "VIEW_EVENT_DETAIL", t0));
        FunnelSession mockSession = mock(FunnelSession.class);
        mockActions(EVENT_ID, actions);
        given(funnelSessionRepository.findBySessionId("session-9:" + EVENT_ID))
                .willReturn(Optional.of(mockSession));

        service.reconstruct(EVENT_ID, TARGET_DATE);

        verify(mockSession).mergeLaterActions(
                eq(FunnelStep.VIEW_EVENT_DETAIL), eq(false), eq(false), eq(t0), any());
        verify(funnelSessionRepository, never()).save(any());
        verify(visitorProfileRepository, never()).findByVisitorKey(any());
    }

    @Test
    void reconstruct_lateCompletePaymentAfterSessionAlreadyExists_advancesMaxStepInsteadOfBeingLost() {
        OffsetDateTime yesterday = OffsetDateTime.parse("2026-08-16T23:55:00+09:00");
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T00:10:00+09:00");
        // 자정을 걸친 세션 — 어제 배치에서 이미 OPEN_PURCHASE_MODAL까지 이탈로 집계된 상태.
        FunnelSession existingFromYesterday = FunnelSession.create(
                "session-10:" + EVENT_ID, EVENT_ID, "anon:anon-1", FunnelStep.OPEN_PURCHASE_MODAL,
                true, false, false, false, yesterday, yesterday, yesterday);
        List<FunnelAction> actions = List.of(action("session-10", "COMPLETE_PAYMENT", t0));
        mockActions(EVENT_ID, actions);
        given(funnelSessionRepository.findBySessionId("session-10:" + EVENT_ID))
                .willReturn(Optional.of(existingFromYesterday));

        service.reconstruct(EVENT_ID, TARGET_DATE);

        assertThat(existingFromYesterday.getMaxStepReached()).isEqualTo(FunnelStep.COMPLETE_PAYMENT);
        assertThat(existingFromYesterday.isDropped()).isFalse();
        verify(funnelSessionRepository, never()).save(any());
    }

    @Test
    void reconstruct_sameRawSessionIdAcrossDifferentEvents_savesSeparateSessionsForEachEvent() {
        // 같은 브라우징 흐름(30분 이내)에서 사용자가 서로 다른 두 행사를 봤다면,
        // FE가 넘기는 원본 sessionId는 두 행사 모두에서 동일하다.
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        Long otherEventId = 99L;
        String sharedSessionId = "shared-session";

        given(visitorProfileRepository.findByVisitorKey(any())).willReturn(Optional.empty());

        FunnelAction actionForOtherEvent = FunnelAction.from(new FunnelActionEventDto(
                UUID.randomUUID().toString(), sharedSessionId, otherEventId, "anon-1", null,
                "VIEW_EVENT_DETAIL", t0, t0, Map.of()));

        mockActions(EVENT_ID, List.of(action(sharedSessionId, "VIEW_EVENT_DETAIL", t0)));
        mockActions(otherEventId, List.of(actionForOtherEvent));

        service.reconstruct(EVENT_ID, TARGET_DATE);
        service.reconstruct(otherEventId, TARGET_DATE);

        ArgumentCaptor<FunnelSession> captor = ArgumentCaptor.forClass(FunnelSession.class);
        verify(funnelSessionRepository, times(2)).save(captor.capture());

        List<String> storedSessionIds = captor.getAllValues().stream()
                .map(FunnelSession::getSessionId)
                .toList();
        assertThat(storedSessionIds).containsExactlyInAnyOrder(
                sharedSessionId + ":" + EVENT_ID,
                sharedSessionId + ":" + otherEventId);
    }

    @Test
    void reconstruct_loggedInUser_usesUserIdBasedVisitorKey() {
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        List<FunnelAction> actions =
                List.of(action("session-10", "VIEW_EVENT_DETAIL", t0, "anon-1", 7L));
        mockActions(EVENT_ID, actions);
        given(visitorProfileRepository.findByVisitorKey("member:7")).willReturn(Optional.empty());

        service.reconstruct(EVENT_ID, TARGET_DATE);

        ArgumentCaptor<FunnelSession> sessionCaptor = ArgumentCaptor.forClass(FunnelSession.class);
        verify(funnelSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getVisitorKey()).isEqualTo("member:7");
    }

    @Test
    void reconstruct_sessionSplitAcrossScrollPages_mergesActionsFromBothPages() {
        // ES scroll이 페이지 크기 제한으로 같은 session_id의 액션을 두 페이지에 나눠 돌려주더라도
        // sessionId 정렬 덕분에 연속으로 도착하므로, 두 페이지에 걸친 액션이 하나의 세션으로 합쳐져야 한다.
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        FunnelAction firstPageAction = action("session-12", "VIEW_EVENT_DETAIL", t0);
        FunnelAction secondPageAction = action("session-12", "COMPLETE_PAYMENT", t0.plusMinutes(1));
        Window<FunnelAction> firstPage =
                Window.from(List.of(firstPageAction), index -> ScrollPosition.offset(index), true);
        Window<FunnelAction> secondPage =
                Window.from(List.of(secondPageAction), index -> ScrollPosition.offset(index), false);
        given(funnelActionRepository.findFirst500ByEventIdAndReceivedAtBetweenOrderBySessionIdAscActionIdAsc(
                eq(EVENT_ID), any(), any(), any()))
                .willReturn(firstPage, secondPage);
        given(visitorProfileRepository.findByVisitorKey(org.mockito.ArgumentMatchers.anyString()))
                .willReturn(Optional.empty());

        service.reconstruct(EVENT_ID, TARGET_DATE);

        ArgumentCaptor<FunnelSession> captor = ArgumentCaptor.forClass(FunnelSession.class);
        verify(funnelSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getSessionId()).isEqualTo("session-12:" + EVENT_ID);
        assertThat(captor.getValue().getMaxStepReached()).isEqualTo(FunnelStep.COMPLETE_PAYMENT);
        assertThat(captor.getValue().isDropped()).isFalse();
    }

    @Test
    void reconstruct_repositoryReturnsNullWindowForEmptyDay_completesWithoutThrowing() {
        // Spring Data ES가 해당 날짜에 액션이 하나도 없을 때 빈 Window 대신 null을 돌려주는
        // 경우를 재현한다 (실제 운영에서 당일 재구성 시 NPE로 500이 발생했던 케이스).
        given(funnelActionRepository.findFirst500ByEventIdAndReceivedAtBetweenOrderBySessionIdAscActionIdAsc(
                eq(EVENT_ID), any(), any(), any()))
                .willReturn(null);

        service.reconstruct(EVENT_ID, TARGET_DATE);

        verify(funnelSessionRepository, never()).save(any());
        verify(visitorProfileRepository, never()).findByVisitorKey(any());
    }

    @Test
    void reconstructForAdmin_nullActor_throwsForbidden() {
        assertThatThrownBy(() -> service.reconstructForAdmin(EVENT_ID, TARGET_DATE, null))
                .isInstanceOf(BusinessException.class);
        verify(funnelActionRepository, never())
                .findFirst500ByEventIdAndReceivedAtBetweenOrderBySessionIdAscActionIdAsc(any(), any(), any(), any());
    }

    @Test
    void reconstructForAdmin_nonAdminRole_throwsForbidden() {
        AuthenticatedMemberDto generalUser = new AuthenticatedMemberDto(2L, PlatformRole.USER);

        assertThatThrownBy(() -> service.reconstructForAdmin(EVENT_ID, TARGET_DATE, generalUser))
                .isInstanceOf(BusinessException.class);
        verify(funnelActionRepository, never())
                .findFirst500ByEventIdAndReceivedAtBetweenOrderBySessionIdAscActionIdAsc(any(), any(), any(), any());
    }

    @Test
    void reconstructForAdmin_adminActor_delegatesToReconstruct() {
        AuthenticatedMemberDto admin = new AuthenticatedMemberDto(1L, PlatformRole.PLATFORM_ADMIN);
        OffsetDateTime t0 = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        List<FunnelAction> actions = List.of(action("session-11", "VIEW_EVENT_DETAIL", t0));
        mockActions(EVENT_ID, actions);
        given(visitorProfileRepository.findByVisitorKey("anon:anon-1")).willReturn(Optional.empty());

        service.reconstructForAdmin(EVENT_ID, TARGET_DATE, admin);

        verify(funnelSessionRepository).save(any(FunnelSession.class));
    }
}
