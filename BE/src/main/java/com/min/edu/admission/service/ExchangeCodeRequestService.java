package com.min.edu.admission.service;

import com.min.edu.admission.domain.ExchangeCodeRequest;
import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.admission.dto.ExchangeCodeRequestDtos;
import com.min.edu.admission.dto.ExchangeCodeRequestView;
import com.min.edu.admission.repository.ExchangeCodeRequestRepository;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.event.policy.EventOperationDeadlinePolicy;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExchangeCodeRequestService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MIN_REQUESTED_QUANTITY = 1;
    private static final int MAX_REQUESTED_QUANTITY = 1000;
    private static final int MAX_PURPOSE_LENGTH = 500;
    private static final int MAX_REJECTION_REASON_LENGTH = 2000;
    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);

    private final ExchangeCodeRequestRepository exchangeCodeRequestRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventOrganizationMemberRepository organizationMemberRepository;
    private final ExchangeCodeRequestExceptionTranslator exceptionTranslator;
    private final EventOperationDeadlinePolicy deadlinePolicy;

    public ExchangeCodeRequestService(
            ExchangeCodeRequestRepository exchangeCodeRequestRepository,
            EventRepository eventRepository,
            EventMemberRepository eventMemberRepository,
            EventOrganizationMemberRepository organizationMemberRepository,
            ExchangeCodeRequestExceptionTranslator exceptionTranslator,
            EventOperationDeadlinePolicy deadlinePolicy) {
        this.exchangeCodeRequestRepository = exchangeCodeRequestRepository;
        this.eventRepository = eventRepository;
        this.eventMemberRepository = eventMemberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.exceptionTranslator = exceptionTranslator;
        this.deadlinePolicy = deadlinePolicy;
    }

    @Transactional
    public ExchangeCodeRequestDtos.CreateResponse createRequest(
            Long eventId,
            ExchangeCodeRequestDtos.CreateRequest request,
            AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        Event event = getEvent(eventId);
        requireEventManager(event, actor);
        validateCreateRequest(request);
        validateEventRequestable(event, OffsetDateTime.now());
        validateNoRequestedRequest(eventId);

        ExchangeCodeRequest exchangeCodeRequest = ExchangeCodeRequest.create(
            eventId,
            actor.getMemberId(),
            request.requestedQuantity(),
            request.purpose(),
            OffsetDateTime.now()
        );

        try {
            return ExchangeCodeRequestDtos.CreateResponse.from(
                exchangeCodeRequestRepository.save(exchangeCodeRequest)
            );
        } catch (DataIntegrityViolationException exception) {
            BusinessException businessException = exceptionTranslator.translate(exception);
            if (businessException != null) {
                throw businessException;
            }
            throw exception;
        }
    }

    public Page<ExchangeCodeRequestDtos.Response> getEventRequests(
            Long eventId,
            ExchangeCodeRequestStatus status,
            AuthenticatedMemberDto actor,
            Pageable pageable) {
        requireAuthenticated(actor);
        Event event = getEvent(eventId);
        requireEventManager(event, actor);
        return exchangeCodeRequestRepository
            .findEventRequests(eventId, status, normalize(pageable))
            .map(ExchangeCodeRequestDtos.Response::from);
    }

    public ExchangeCodeRequestDtos.Response getRequestDetail(
            Long requestId,
            AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        ExchangeCodeRequestView detail = exchangeCodeRequestRepository
            .findRequestDetail(requestId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_NOT_FOUND));

        requireAdminOrEventManager(detail.getEventId(), actor);
        return ExchangeCodeRequestDtos.Response.from(detail);
    }

    public Page<ExchangeCodeRequestDtos.Response> getAdminRequests(
            ExchangeCodeRequestStatus status,
            AuthenticatedMemberDto actor,
            Pageable pageable) {
        requireAdmin(actor);
        ExchangeCodeRequestStatus effectiveStatus =
            status == null ? ExchangeCodeRequestStatus.REQUESTED : status;
        return exchangeCodeRequestRepository
            .findAdminRequests(effectiveStatus, normalize(pageable))
            .map(ExchangeCodeRequestDtos.Response::from);
    }

    @Transactional
    public void approveRequest(Long requestId, AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        ExchangeCodeRequest request = exchangeCodeRequestRepository
            .findByIdForUpdate(requestId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_NOT_FOUND));
        transition(() -> request.approve(actor.getMemberId(), OffsetDateTime.now()));
    }

    @Transactional
    public void rejectRequest(
            Long requestId,
            String reason,
            AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        validateRejectionReason(reason);
        ExchangeCodeRequest request = exchangeCodeRequestRepository
            .findByIdForUpdate(requestId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_NOT_FOUND));
        transition(() -> request.reject(actor.getMemberId(), reason, OffsetDateTime.now()));
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
    }

    private void validateEventRequestable(Event event, OffsetDateTime now) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EVENT_NOT_OPEN);
        }
        if (!deadlinePolicy.isBeforeOperationCutoff(now, event.getEndAt())) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EVENT_ALREADY_STARTED);
        }
    }

    private void validateCreateRequest(ExchangeCodeRequestDtos.CreateRequest request) {
        if (request.requestedQuantity() == null
                || request.requestedQuantity() < MIN_REQUESTED_QUANTITY
                || request.requestedQuantity() > MAX_REQUESTED_QUANTITY
                || request.purpose() == null
                || request.purpose().isBlank()
                || request.purpose().length() > MAX_PURPOSE_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateRejectionReason(String reason) {
        if (reason == null
                || reason.isBlank()
                || reason.length() > MAX_REJECTION_REASON_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateNoRequestedRequest(Long eventId) {
        if (exchangeCodeRequestRepository.existsByEventIdAndStatus(
                eventId, ExchangeCodeRequestStatus.REQUESTED)) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_EXISTS);
        }
    }

    private void requireAdminOrEventManager(Long eventId, AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        if (actor.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return;
        }
        requireEventManager(getEvent(eventId), actor);
    }

    private void requireEventManager(Event event, AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        boolean organizerManager = organizationMemberRepository
            .existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                event.getOrganizerOrganizationId(),
                actor.getMemberId(),
                OrganizationMemberStatus.ACTIVE,
                MANAGER_ROLES
            );
        boolean assigned = eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            event.getId(),
            actor.getMemberId(),
            EventRole.EVENT_MANAGER
        );
        if (!organizerManager && !assigned) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private void requireAdmin(AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        if (actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private void requireAuthenticated(AuthenticatedMemberDto actor) {
        if (actor == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    private Pageable normalize(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(DEFAULT_PAGE, DEFAULT_SIZE);
        }
        if (pageable.getPageSize() > MAX_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private void transition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);
        }
    }
}
