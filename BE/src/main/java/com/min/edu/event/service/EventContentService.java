package com.min.edu.event.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventContent;
import com.min.edu.event.domain.EventContentAudience;
import com.min.edu.event.domain.EventContentType;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.dto.EventContentDtos;
import com.min.edu.event.repository.EventContentRepository;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.PlatformRole;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventContentService {

    private final EventContentRepository eventContentRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;

    /**
     * 공지·자료 목록 조회
     *
     * 권한별 audience 필터링:
     * - PLATFORM_ADMIN 또는 해당 행사 EVENT_MANAGER → ALL / EXHIBITOR / VISITOR 전체 조회
     * - 그 외 (일반 회원·비로그인) → ALL audience만 조회
     *
     * @param eventId     행사 ID
     * @param contentType 콘텐츠 유형 필터 (null이면 전체)
     * @param member      인증 회원 (비로그인이면 null)
     */
    public List<EventContentDtos.Summary> listContents(
            Long eventId,
            EventContentType contentType,
            AuthenticatedMemberDto member) {

        // 행사 존재 여부 확인
        if (!eventRepository.existsById(eventId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        // 접근 가능한 audience 목록 결정
        List<EventContentAudience> allowedAudiences = resolveAllowedAudiences(eventId, member);

        // Specification 조건 구성 (EventService의 Specification 패턴 참고)
        Specification<EventContent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("eventId"), eventId));
            predicates.add(root.get("audience").in(allowedAudiences));
            if (contentType != null) {
                // contentType 파라미터가 있으면 추가 필터링
                predicates.add(cb.equal(root.get("contentType"), contentType));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        // 상단 고정 공지 우선(pinned DESC), 최신순(publishedAt DESC) 정렬
        Sort sort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishedAt"));

        return eventContentRepository.findAll(spec, sort)
                .stream()
                .map(EventContentDtos.Summary::from)
                .toList();
    }

    /**
     * 공지·자료 상세 조회
     *
     * 권한별 audience 접근 제어:
     * - PLATFORM_ADMIN / 해당 행사 EVENT_MANAGER → audience 제한 없음
     * - 그 외 → ALL audience 콘텐츠만 접근 가능 (EXHIBITOR·VISITOR는 403)
     *
     * @param contentId 콘텐츠 ID
     * @param member    인증 회원 (비로그인이면 null)
     */
    public EventContentDtos.Summary getContent(Long contentId, AuthenticatedMemberDto member) {

        // 콘텐츠 조회
        EventContent content = eventContentRepository.findById(contentId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 접근 가능한 audience 목록 결정 후 권한 검증
        List<EventContentAudience> allowedAudiences =
                resolveAllowedAudiences(content.getEventId(), member);

        if (!allowedAudiences.contains(content.getAudience())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        return EventContentDtos.Summary.from(content);
    }

    /**
     * 권한에 따른 조회 가능 audience 목록 결정
     * - PLATFORM_ADMIN / 해당 행사 EVENT_MANAGER: 전체 audience (ALL, EXHIBITOR, VISITOR)
     * - 그 외: ALL만
     */
    private List<EventContentAudience> resolveAllowedAudiences(
            Long eventId, AuthenticatedMemberDto member) {
        if (member != null) {
            if (member.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
                return Arrays.asList(EventContentAudience.values());
            }
            if (eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                    eventId, member.getMemberId(), EventRole.EVENT_MANAGER)) {
                return Arrays.asList(EventContentAudience.values());
            }
        }
        return List.of(EventContentAudience.ALL);
    }
}