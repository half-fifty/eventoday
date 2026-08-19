package com.min.edu.event.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventOperationAccessService {

    private static final List<OrganizationRole> MANAGER_ROLES =
        List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventOrganizationMemberRepository organizationMemberRepository;

    public EventOperationAccessService(
            EventRepository eventRepository,
            EventMemberRepository eventMemberRepository,
            EventOrganizationMemberRepository organizationMemberRepository) {
        this.eventRepository = eventRepository;
        this.eventMemberRepository = eventMemberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
    }

    public Event requireOperationalAccess(Long eventId, AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
        requireOperationalAccess(event, actor.getMemberId());
        return event;
    }

    public Event requireOperationalAccess(Long eventId, Long memberId) {
        if (memberId == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
        requireOperationalAccess(event, memberId);
        return event;
    }

    public void requireOperationalAccess(Event event, Long memberId) {
        if (isOrganizerManager(event, memberId)
                || isActiveEventRole(event.getId(), memberId, EventRole.EVENT_MANAGER)
                || isActiveEventRole(event.getId(), memberId, EventRole.CHECKIN_STAFF)) {
            return;
        }
        throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }

    private boolean isOrganizerManager(Event event, Long memberId) {
        return organizationMemberRepository
            .existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                event.getOrganizerOrganizationId(),
                memberId,
                OrganizationMemberStatus.ACTIVE,
                MANAGER_ROLES
            );
    }

    private boolean isActiveEventRole(Long eventId, Long memberId, EventRole role) {
        return eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            eventId,
            memberId,
            role
        );
    }

    private void requireAuthenticated(AuthenticatedMemberDto actor) {
        if (actor == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }
}
