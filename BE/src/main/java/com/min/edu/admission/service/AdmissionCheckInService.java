package com.min.edu.admission.service;

import com.min.edu.admission.domain.AdmissionAction;
import com.min.edu.admission.domain.AdmissionResult;
import com.min.edu.admission.dto.AdmissionCheckInDtos;
import com.min.edu.admission.dto.AdmissionLogView;
import com.min.edu.admission.repository.AdmissionLogRepository;
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
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdmissionCheckInService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MAX_GATE_NAME_LENGTH = 100;
    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventOrganizationMemberRepository organizationMemberRepository;
    private final AdmissionLogRepository admissionLogRepository;
    private final AdmissionCheckInProcessor processor;

    public AdmissionCheckInService(
            EventRepository eventRepository,
            EventMemberRepository eventMemberRepository,
            EventOrganizationMemberRepository organizationMemberRepository,
            AdmissionLogRepository admissionLogRepository,
            AdmissionCheckInProcessor processor) {
        this.eventRepository = eventRepository;
        this.eventMemberRepository = eventMemberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.admissionLogRepository = admissionLogRepository;
        this.processor = processor;
    }

    public AdmissionCheckInDtos.CheckInResponse checkIn(
            Long eventId,
            AdmissionCheckInDtos.CheckInRequest request,
            AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        Event event = findEvent(eventId);
        requireCheckInStaff(event, actor);
        String qrToken = validateQrToken(request);
        String gateName = normalizeGateName(request.gateName());

        AdmissionCheckInProcessor.ProcessResult result;
        try {
            result = processor.checkIn(
                eventId,
                event,
                qrToken,
                gateName,
                actor.getMemberId(),
                OffsetDateTime.now()
            );
        } catch (PessimisticLockingFailureException exception) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_CHECK_IN_PROCESSING_CONFLICT);
        }
        if (result.outcome() == AdmissionCheckInProcessor.Outcome.DUPLICATE) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_CHECK_IN_DUPLICATE);
        }
        if (result.outcome() == AdmissionCheckInProcessor.Outcome.INVALID) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_CHECK_IN_INVALID_STATE);
        }
        return new AdmissionCheckInDtos.CheckInResponse(
            result.admissionTicketId(),
            result.eventId(),
            result.eventName(),
            result.status(),
            result.usedAt(),
            result.admissionLogId(),
            result.action(),
            result.result(),
            result.processedAt()
        );
    }

    public AdmissionCheckInDtos.CheckInCancellationResponse cancelCheckIn(
            Long eventId,
            Long admissionTicketId,
            AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        Event event = findEvent(eventId);
        requireCheckInStaff(event, actor);

        AdmissionCheckInProcessor.ProcessResult result;
        try {
            result = processor.cancelCheckIn(
                eventId,
                event,
                admissionTicketId,
                actor.getMemberId(),
                OffsetDateTime.now()
            );
        } catch (PessimisticLockingFailureException exception) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_CHECK_IN_PROCESSING_CONFLICT);
        }
        if (result.outcome() == AdmissionCheckInProcessor.Outcome.CANCEL_INVALID) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_CHECK_IN_CANCEL_INVALID_STATE);
        }
        return new AdmissionCheckInDtos.CheckInCancellationResponse(
            result.admissionTicketId(),
            result.eventId(),
            result.eventName(),
            result.status(),
            result.usedAt(),
            result.admissionLogId(),
            result.action(),
            result.result(),
            result.processedAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<AdmissionCheckInDtos.LogListResponse> getEventAdmissionLogs(
            Long eventId,
            AdmissionAction action,
            AdmissionResult result,
            AuthenticatedMemberDto actor,
            Integer page,
            Integer size) {
        requireAuthenticated(actor);
        Event event = findEvent(eventId);
        requireEventLogManager(event, actor);
        Pageable pageable = normalize(page, size);
        return admissionLogRepository.findEventAdmissionLogs(eventId, action, result, pageable)
            .map(this::toLogListResponse);
    }

    private AdmissionCheckInDtos.LogListResponse toLogListResponse(AdmissionLogView view) {
        return new AdmissionCheckInDtos.LogListResponse(
            view.getAdmissionLogId(),
            view.getAdmissionTicketId(),
            view.getAction(),
            view.getResult(),
            view.getGateName(),
            view.getStaffNickname(),
            view.getProcessedAt()
        );
    }

    private void requireAuthenticated(AuthenticatedMemberDto actor) {
        if (actor == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
    }

    private String validateQrToken(AdmissionCheckInDtos.CheckInRequest request) {
        if (request == null || request.qrToken() == null || request.qrToken().isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        String qrToken = request.qrToken().trim();
        if (qrToken.isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return qrToken;
    }

    private String normalizeGateName(String gateName) {
        if (gateName == null) {
            return null;
        }
        String normalized = gateName.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > MAX_GATE_NAME_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    private Pageable normalize(Integer page, Integer size) {
        int pageNumber = page == null ? DEFAULT_PAGE : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return PageRequest.of(pageNumber, pageSize);
    }

    private void requireCheckInStaff(Event event, AuthenticatedMemberDto actor) {
        if (isOrganizerManager(event, actor)) {
            return;
        }
        if (isActiveEventRole(event.getId(), actor.getMemberId(), EventRole.EVENT_MANAGER)) {
            return;
        }
        if (isActiveEventRole(event.getId(), actor.getMemberId(), EventRole.CHECKIN_STAFF)) {
            return;
        }
        throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }

    private void requireEventLogManager(Event event, AuthenticatedMemberDto actor) {
        if (isOrganizerManager(event, actor)) {
            return;
        }
        if (isActiveEventRole(event.getId(), actor.getMemberId(), EventRole.EVENT_MANAGER)) {
            return;
        }
        throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }

    private boolean isOrganizerManager(Event event, AuthenticatedMemberDto actor) {
        return organizationMemberRepository
            .existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                event.getOrganizerOrganizationId(),
                actor.getMemberId(),
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
}
