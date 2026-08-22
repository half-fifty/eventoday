package com.min.edu.funnel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.event.service.EventOperationAccessService;
import com.min.edu.funnel.domain.FunnelSession;
import com.min.edu.funnel.domain.FunnelStep;
import com.min.edu.funnel.dto.FunnelEventRankingResponse;
import com.min.edu.funnel.dto.FunnelSessionSummaryResponse;
import com.min.edu.funnel.repository.FunnelSessionRepository;
import com.min.edu.member.domain.PlatformRole;

@ExtendWith(MockitoExtension.class)
class FunnelSessionSummaryServiceTest {

    private static final Long EVENT_ID = 42L;
    private static final Long ORGANIZATION_ID = 7L;
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 17);
    private static final AuthenticatedMemberDto ADMIN =
            new AuthenticatedMemberDto(1L, PlatformRole.PLATFORM_ADMIN);
    private static final AuthenticatedMemberDto ORGANIZER =
            new AuthenticatedMemberDto(3L, PlatformRole.USER);

    @Mock
    private FunnelSessionRepository funnelSessionRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventOperationAccessService eventOperationAccessService;

    @InjectMocks
    private FunnelSessionSummaryService service;

    private FunnelSession session(
            String sessionId, Long eventId, FunnelStep maxStep, boolean dropped, boolean boothExplored,
            boolean returningVisitor) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-17T10:00:00+09:00");
        return FunnelSession.create(
                sessionId, eventId, "anon:" + sessionId, maxStep,
                dropped, returningVisitor, boothExplored, false, now, now, now);
    }

    private FunnelSession session(
            String sessionId, FunnelStep maxStep, boolean dropped, boolean boothExplored, boolean returningVisitor) {
        return session(sessionId, EVENT_ID, maxStep, dropped, boothExplored, returningVisitor);
    }

    private Event event(Long id, String name) {
        return Event.builder().id(id).name(name).organizerOrganizationId(ORGANIZATION_ID).build();
    }

    @Test
    void summarizeForAdmin_nullActor_throwsForbidden() {
        assertThatThrownBy(() -> service.summarizeForAdmin(EVENT_ID, REPORT_DATE, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void summarizeForAdmin_nonAdminRole_throwsForbidden() {
        AuthenticatedMemberDto generalUser = new AuthenticatedMemberDto(2L, PlatformRole.USER);

        assertThatThrownBy(() -> service.summarizeForAdmin(EVENT_ID, REPORT_DATE, generalUser))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void summarizeForAdmin_countsSessionsByStepCumulatively() {
        List<FunnelSession> sessions = List.of(
                session("s1", FunnelStep.COMPLETE_PAYMENT, false, true, false),
                session("s2", FunnelStep.OPEN_PURCHASE_MODAL, true, false, true),
                session("s3", FunnelStep.VIEW_EVENT_DETAIL, true, false, false),
                session("s4", FunnelStep.VISIT, true, false, false));
        given(funnelSessionRepository.findByEventIdAndStartedAtBetween(any(), any(), any()))
                .willReturn(sessions);

        FunnelSessionSummaryResponse response = service.summarizeForAdmin(EVENT_ID, REPORT_DATE, ADMIN);

        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.reportDate()).isEqualTo(REPORT_DATE);
        assertThat(response.totalSessions()).isEqualTo(4);
        assertThat(response.viewEventDetailCount()).isEqualTo(3);   // s1,s2,s3 (COMPLETE_PAYMENT/OPEN_PURCHASE_MODAL/VIEW_EVENT_DETAIL 모두 이 단계 이상)
        assertThat(response.openPurchaseModalCount()).isEqualTo(2); // s1,s2
        assertThat(response.completePaymentCount()).isEqualTo(1);  // s1
        assertThat(response.droppedCount()).isEqualTo(3);          // s2,s3,s4
        assertThat(response.boothExploredCount()).isEqualTo(1);    // s1
        assertThat(response.returningVisitorCount()).isEqualTo(1); // s2
    }

    @Test
    void summarizeForAdmin_noSessions_returnsAllZero() {
        given(funnelSessionRepository.findByEventIdAndStartedAtBetween(any(), any(), any()))
                .willReturn(List.of());

        FunnelSessionSummaryResponse response = service.summarizeForAdmin(EVENT_ID, REPORT_DATE, ADMIN);

        assertThat(response.totalSessions()).isZero();
        assertThat(response.completePaymentCount()).isZero();
    }

    @Test
    void summarizeForOrganizer_ownsEvent_returnsSummary() {
        given(eventOperationAccessService.requireOperationalAccess(EVENT_ID, ORGANIZER))
                .willReturn(event(EVENT_ID, "가을 박람회"));
        given(funnelSessionRepository.findByEventIdAndStartedAtBetween(any(), any(), any()))
                .willReturn(List.of(session("s1", FunnelStep.COMPLETE_PAYMENT, false, false, false)));

        FunnelSessionSummaryResponse response =
                service.summarizeForOrganizer(ORGANIZATION_ID, EVENT_ID, REPORT_DATE, ORGANIZER);

        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.totalSessions()).isEqualTo(1);
    }

    @Test
    void summarizeForOrganizer_eventBelongsToOtherOrganization_throwsNotFound() {
        given(eventOperationAccessService.requireOperationalAccess(EVENT_ID, ORGANIZER))
                .willReturn(event(EVENT_ID, "가을 박람회"));

        assertThatThrownBy(() -> service.summarizeForOrganizer(999L, EVENT_ID, REPORT_DATE, ORGANIZER))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void summarizeForOrganizer_notOperationalManager_propagatesAccessDenial() {
        willThrow(new BusinessException(GlobalErrorCode.FORBIDDEN))
                .given(eventOperationAccessService).requireOperationalAccess(EVENT_ID, ORGANIZER);

        assertThatThrownBy(() -> service.summarizeForOrganizer(ORGANIZATION_ID, EVENT_ID, REPORT_DATE, ORGANIZER))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rankEventsByDate_nonAdminRole_throwsForbidden() {
        assertThatThrownBy(() -> service.rankEventsByDate(REPORT_DATE, ORGANIZER))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rankEventsByDate_multipleEvents_sortedByTotalSessionsDescending() {
        Long popularEventId = 10L;
        Long quietEventId = 20L;
        List<FunnelSession> sessions = List.of(
                session("s1", quietEventId, FunnelStep.VISIT, true, false, false),
                session("s2", popularEventId, FunnelStep.COMPLETE_PAYMENT, false, false, false),
                session("s3", popularEventId, FunnelStep.VIEW_EVENT_DETAIL, true, false, false));
        given(funnelSessionRepository.findByStartedAtBetween(any(), any())).willReturn(sessions);
        given(eventRepository.findAllById(any())).willReturn(List.of(
                event(popularEventId, "인기 행사"),
                event(quietEventId, "조용한 행사")));

        FunnelEventRankingResponse response = service.rankEventsByDate(REPORT_DATE, ADMIN);

        assertThat(response.reportDate()).isEqualTo(REPORT_DATE);
        assertThat(response.events()).extracting("eventId", "eventName", "totalSessions", "completePaymentCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(popularEventId, "인기 행사", 2L, 1L),
                        org.assertj.core.groups.Tuple.tuple(quietEventId, "조용한 행사", 1L, 0L));
    }

    @Test
    void rankEventsByDate_noSessions_returnsEmptyList() {
        given(funnelSessionRepository.findByStartedAtBetween(any(), any())).willReturn(List.of());

        FunnelEventRankingResponse response = service.rankEventsByDate(REPORT_DATE, ADMIN);

        assertThat(response.events()).isEmpty();
    }
}
