package com.min.edu.funnel.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.funnel.domain.FunnelAction;
import com.min.edu.funnel.domain.FunnelSession;
import com.min.edu.funnel.domain.FunnelStep;
import com.min.edu.funnel.domain.VisitorProfile;
import com.min.edu.funnel.repository.FunnelActionRepository;
import com.min.edu.funnel.repository.FunnelSessionRepository;
import com.min.edu.funnel.repository.VisitorProfileRepository;
import com.min.edu.member.domain.PlatformRole;

import lombok.RequiredArgsConstructor;

/**
 * 일일 배치가 Elasticsearch의 원본 FunnelAction을 session_id 기준으로 묶어
 * FunnelSession(+VisitorProfile)을 사후 계산한다 (technical-design.md 참고).
 * 이미 처리된 session_id를 다시 만나면(같은 날짜 재실행, 또는 자정을 걸친 세션/결제 확정
 * 지연으로 다음 날 배치가 나머지 액션을 발견한 경우) 새로 만들지 않고 기존 요약에 병합한다.
 */
@Service
@RequiredArgsConstructor
public class FunnelSessionReconstructionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String BOOTH_LIST_ACTION_TYPE = "VIEW_BOOTH_LIST";

    private final FunnelActionRepository funnelActionRepository;
    private final FunnelSessionRepository funnelSessionRepository;
    private final VisitorProfileRepository visitorProfileRepository;

    // 새벽 배치를 기다리지 않고 관리자가 특정 날짜치를 즉시 재구성해볼 수 있는 수동 트리거 (개발/확인용).
    @Transactional
    public void reconstructForAdmin(Long eventId, LocalDate targetDate, AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        reconstruct(eventId, targetDate);
    }

    private void requireAdmin(AuthenticatedMemberDto actor) {
        if (actor == null || actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    // 배치 진입점: 행사의 하루치 원본 액션을 ES에서 읽어와 session_id로 묶고, 시작 시각 순으로 세션을 처리한다.
    @Transactional
    public void reconstruct(Long eventId, LocalDate targetDate) {
        OffsetDateTime dayStart = targetDate.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime dayEnd = targetDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime();

        List<FunnelAction> actions =
                funnelActionRepository.findByEventIdAndReceivedAtBetween(eventId, dayStart, dayEnd);

        Map<String, List<FunnelAction>> actionsBySession = actions.stream()
                .collect(Collectors.groupingBy(FunnelAction::getSessionId));

        actionsBySession.values().stream()
                .sorted(Comparator.comparing(this::earliestReceivedAt))
                .forEach(this::reconstructSession);
    }

    // 세션 하나를 판정해서 FunnelSession으로 저장하거나, 이미 있으면 기존 요약에 병합한다.
    private void reconstructSession(List<FunnelAction> sessionActions) {
        FunnelAction first = sessionActions.get(0);
        // 원본 session_id는 30분 안에 여러 행사를 오가면 그대로 재사용될 수 있다(같은 브라우징 흐름).
        // event_id를 합쳐서 저장해야 FunnelSession.session_id 유니크 제약과 부딪히지 않고,
        // 행사별로 독립된 세션 레코드가 만들어진다.
        String storedSessionId = first.getSessionId() + ":" + first.getEventId();

        Set<String> actionTypes = sessionActions.stream()
                .map(FunnelAction::getActionType)
                .collect(Collectors.toSet());

        FunnelStep maxStepReached = resolveMaxStep(actionTypes);
        boolean boothExplored = actionTypes.contains(BOOTH_LIST_ACTION_TYPE);
        boolean stepSkipped = resolveStepSkipped(actionTypes);

        OffsetDateTime startedAt = earliestReceivedAt(sessionActions);
        OffsetDateTime lastActionAt = sessionActions.stream()
                .map(FunnelAction::getReceivedAt)
                .max(OffsetDateTime::compareTo)
                .orElse(startedAt);

        Optional<FunnelSession> existing = funnelSessionRepository.findBySessionId(storedSessionId);
        if (existing.isPresent()) {
            // 자정을 걸친 세션이나 결제 확정 지연으로 다음 날 배치가 나머지 액션을 발견한 경우 —
            // 건너뛰면 늦게 도착한 COMPLETE_PAYMENT가 영구히 누락된다. 관리 상태 엔티티라
            // 트랜잭션 커밋 시 더티 체킹으로 반영되므로 별도 save 호출이 필요 없다.
            existing.get().mergeLaterActions(maxStepReached, boothExplored, stepSkipped, lastActionAt,
                    OffsetDateTime.now());
            return;
        }

        boolean dropped = maxStepReached != FunnelStep.COMPLETE_PAYMENT;
        String visitorKey = resolveVisitorKey(first);
        boolean returningVisitor =
                recordVisitorProfile(visitorKey, first.getAnonymousId(), first.getUserId(), startedAt);

        funnelSessionRepository.save(FunnelSession.create(
                storedSessionId,
                first.getEventId(),
                visitorKey,
                maxStepReached,
                dropped,
                returningVisitor,
                boothExplored,
                stepSkipped,
                startedAt,
                lastActionAt,
                OffsetDateTime.now()));
    }

    // 이 세션이 도달한 최대 단계를 판정한다 (높은 단계부터 체크).
    private FunnelStep resolveMaxStep(Set<String> actionTypes) {
        if (actionTypes.contains(FunnelStep.COMPLETE_PAYMENT.name())) {
            return FunnelStep.COMPLETE_PAYMENT;
        }
        if (actionTypes.contains(FunnelStep.OPEN_PURCHASE_MODAL.name())) {
            return FunnelStep.OPEN_PURCHASE_MODAL;
        }
        if (actionTypes.contains(FunnelStep.VIEW_EVENT_DETAIL.name())) {
            return FunnelStep.VIEW_EVENT_DETAIL;
        }
        return FunnelStep.VISIT;
    }

    /**
     * 선행 단계 이벤트 없이 후속 단계 이벤트만 도착한 세션을 표시한다
     * (예: 행사상세 조회 없이 바로 구매모달 이벤트가 온 경우).
     */
    private boolean resolveStepSkipped(Set<String> actionTypes) {
        boolean hasViewDetail = actionTypes.contains(FunnelStep.VIEW_EVENT_DETAIL.name());
        boolean hasOpenModal = actionTypes.contains(FunnelStep.OPEN_PURCHASE_MODAL.name());
        boolean hasPayment = actionTypes.contains(FunnelStep.COMPLETE_PAYMENT.name());

        if (hasOpenModal && !hasViewDetail) {
            return true;
        }
        return hasPayment && !hasOpenModal;
    }

    // 로그인 여부에 따라 방문자 식별자를 통일된 형태로 만든다 (member:userId 또는 anon:anonymousId).
    private String resolveVisitorKey(FunnelAction action) {
        if (action.getUserId() != null) {
            return "member:" + action.getUserId();
        }
        return "anon:" + action.getAnonymousId();
    }

    /**
     * @return 이 세션이 재방문이면 true. 프로필이 없으면 새로 만들고 false를 반환한다.
     *         기존 프로필은 관리 상태 엔티티라 recordVisit만 호출하면 트랜잭션 커밋 시
     *         더티 체킹으로 반영된다 (별도 save 호출 불필요).
     */
    private boolean recordVisitorProfile(String visitorKey, String anonymousId, Long userId, OffsetDateTime visitAt) {
        return visitorProfileRepository.findByVisitorKey(visitorKey)
                .map(profile -> {
                    profile.recordVisit(visitAt);
                    return true;
                })
                .orElseGet(() -> {
                    visitorProfileRepository.save(VisitorProfile.create(visitorKey, anonymousId, userId, visitAt));
                    return false;
                });
    }

    // 세션(또는 액션 목록)에서 가장 이른 receivedAt을 찾는다 — 정렬 기준/세션 시작 시각 계산에 재사용.
    private OffsetDateTime earliestReceivedAt(List<FunnelAction> actions) {
        return actions.stream()
                .map(FunnelAction::getReceivedAt)
                .min(OffsetDateTime::compareTo)
                .orElseThrow();
    }
}
