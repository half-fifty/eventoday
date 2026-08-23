package com.min.edu.funnel.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.event.service.EventOperationAccessService;
import com.min.edu.funnel.domain.FunnelSession;
import com.min.edu.funnel.domain.FunnelStep;
import com.min.edu.funnel.dto.FunnelEventRankingItem;
import com.min.edu.funnel.dto.FunnelEventRankingResponse;
import com.min.edu.funnel.dto.FunnelSessionSummaryResponse;
import com.min.edu.funnel.repository.FunnelSessionRepository;
import com.min.edu.member.domain.PlatformRole;

import lombok.RequiredArgsConstructor;

/**
 * FunnelSession 원본을 그 자리에서 집계해 보여준다. 이상탐지/AI 코멘트는 아직 없다
 * (P0.5/P1에서 FunnelDiagnosisReport로 대체될 예정).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FunnelSessionSummaryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final FunnelSessionRepository funnelSessionRepository;
    private final EventRepository eventRepository;
    private final EventOperationAccessService eventOperationAccessService;

    public FunnelSessionSummaryResponse summarizeForAdmin(
            Long eventId, LocalDate reportDate, AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        return summarize(eventId, reportDate);
    }

    // 개최자는 자기 조직이 운영하는 행사만 볼 수 있다 (EventOperationAccessService가 소유권을 검증).
    public FunnelSessionSummaryResponse summarizeForOrganizer(
            Long organizationId, Long eventId, LocalDate reportDate, AuthenticatedMemberDto actor) {
        Event event = eventOperationAccessService.requireOperationalAccess(eventId, actor);
        if (!organizationId.equals(event.getOrganizerOrganizationId())) {
            throw new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND);
        }
        return summarize(eventId, reportDate);
    }

    // 선택한 날짜에 방문이 있었던 모든 행사를 방문수 내림차순으로 랭킹한다 (플랫폼 관리자 전용).
    public FunnelEventRankingResponse rankEventsByDate(LocalDate reportDate, AuthenticatedMemberDto actor) {
        requireAdmin(actor);

        List<FunnelSession> sessions = funnelSessionRepository.findByStartedAtGreaterThanEqualAndStartedAtLessThan(
                reportDate.atStartOfDay(KST).toOffsetDateTime(),
                reportDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime());

        Map<Long, List<FunnelSession>> sessionsByEvent =
                sessions.stream().collect(Collectors.groupingBy(FunnelSession::getEventId));
        Map<Long, String> eventNames = eventRepository.findAllById(sessionsByEvent.keySet()).stream()
                .collect(Collectors.toMap(Event::getId, Event::getName));

        List<FunnelEventRankingItem> items = sessionsByEvent.entrySet().stream()
                .map(entry -> new FunnelEventRankingItem(
                        entry.getKey(),
                        eventNames.getOrDefault(entry.getKey(), "알 수 없는 행사"),
                        entry.getValue().size(),
                        countAtLeast(entry.getValue(), FunnelStep.COMPLETE_PAYMENT)))
                .sorted(Comparator.comparingLong(FunnelEventRankingItem::totalSessions).reversed())
                .toList();

        return new FunnelEventRankingResponse(reportDate, items);
    }

    private FunnelSessionSummaryResponse summarize(Long eventId, LocalDate reportDate) {
        OffsetDateTime dayStart = reportDate.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime dayEnd = reportDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime();

        List<FunnelSession> sessions = funnelSessionRepository
                .findByEventIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eventId, dayStart, dayEnd);

        return new FunnelSessionSummaryResponse(
                eventId,
                reportDate,
                sessions.size(),
                countAtLeast(sessions, FunnelStep.VIEW_EVENT_DETAIL),
                countAtLeast(sessions, FunnelStep.OPEN_PURCHASE_MODAL),
                countAtLeast(sessions, FunnelStep.COMPLETE_PAYMENT),
                sessions.stream().filter(FunnelSession::isDropped).count(),
                sessions.stream().filter(FunnelSession::isBoothExplored).count(),
                sessions.stream().filter(FunnelSession::isReturningVisitor).count());
    }

    // FunnelStep은 선언 순서가 곧 퍼널 진행 순서라, ordinal 비교로 "이 단계 이상 도달"을 판정한다.
    private long countAtLeast(List<FunnelSession> sessions, FunnelStep step) {
        return sessions.stream()
                .filter(session -> session.getMaxStepReached().ordinal() >= step.ordinal())
                .count();
    }

    private void requireAdmin(AuthenticatedMemberDto actor) {
        if (actor == null || actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }
}
