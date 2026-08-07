package com.min.edu.admission.service;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.dto.ExchangeCodeDtos;
import com.min.edu.admission.dto.ExchangeCodeView;
import com.min.edu.admission.repository.ExchangeCodeRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExchangeCodeQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int NORMAL_CODE_PREFIX_LENGTH = 4;
    private static final int NORMAL_CODE_SUFFIX_LENGTH = 4;
    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);

    private final ExchangeCodeRepository exchangeCodeRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventOrganizationMemberRepository organizationMemberRepository;

    public ExchangeCodeQueryService(
            ExchangeCodeRepository exchangeCodeRepository,
            EventRepository eventRepository,
            EventMemberRepository eventMemberRepository,
            EventOrganizationMemberRepository organizationMemberRepository) {
        this.exchangeCodeRepository = exchangeCodeRepository;
        this.eventRepository = eventRepository;
        this.eventMemberRepository = eventMemberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
    }

    public Page<ExchangeCodeDtos.EventListResponse> getEventExchangeCodes(
            Long eventId,
            ExchangeCodeStatus status,
            AuthenticatedMemberDto actor,
            Integer page,
            Integer size) {
        requireAuthenticated(actor);
        Event event = getEvent(eventId);
        requireEventManager(event, actor);
        Pageable pageable = normalize(page, size);

        return exchangeCodeRepository
            .findEventExchangeCodes(eventId, status, pageable)
            .map(this::toEventListResponse);
    }

    public Page<ExchangeCodeDtos.MyListResponse> getMyExchangeCodes(
            ExchangeCodeStatus status,
            AuthenticatedMemberDto actor,
            Integer page,
            Integer size) {
        requireAuthenticated(actor);
        Pageable pageable = normalize(page, size);

        return exchangeCodeRepository
            .findMyExchangeCodes(actor.getMemberId(), status, pageable)
            .map(this::toMyListResponse);
    }

    private ExchangeCodeDtos.EventListResponse toEventListResponse(ExchangeCodeView view) {
        return new ExchangeCodeDtos.EventListResponse(
            view.getExchangeCodeId(),
            view.getEventId(),
            view.getEventName(),
            mask(view.getCode()),
            source(view),
            view.getStatus(),
            view.getHolderNickname(),
            view.getExpiresAt(),
            view.getRedeemedAt(),
            view.getCreatedAt()
        );
    }

    private ExchangeCodeDtos.MyListResponse toMyListResponse(ExchangeCodeView view) {
        return new ExchangeCodeDtos.MyListResponse(
            view.getExchangeCodeId(),
            view.getEventId(),
            view.getEventName(),
            view.getCode(),
            source(view),
            view.getStatus(),
            view.getExpiresAt(),
            view.getRedeemedAt(),
            view.getCreatedAt()
        );
    }

    private ExchangeCodeDtos.Source source(ExchangeCodeView view) {
        if (view.getTicketOrderId() != null) {
            return ExchangeCodeDtos.Source.TICKET_ORDER;
        }
        return ExchangeCodeDtos.Source.EXTERNAL_REQUEST;
    }

    private String mask(String code) {
        if (code.length() <= 2) {
            return "*".repeat(code.length());
        }
        if (code.length() <= NORMAL_CODE_PREFIX_LENGTH + NORMAL_CODE_SUFFIX_LENGTH) {
            return code.charAt(0)
                + "*".repeat(code.length() - 2)
                + code.charAt(code.length() - 1);
        }

        int maskedLength = code.length() - NORMAL_CODE_PREFIX_LENGTH - NORMAL_CODE_SUFFIX_LENGTH;
        return code.substring(0, NORMAL_CODE_PREFIX_LENGTH)
            + "*".repeat(maskedLength)
            + code.substring(code.length() - NORMAL_CODE_SUFFIX_LENGTH);
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
    }

    private void requireEventManager(Event event, AuthenticatedMemberDto actor) {
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

    private void requireAuthenticated(AuthenticatedMemberDto actor) {
        if (actor == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    private Pageable normalize(Integer page, Integer size) {
        int pageNumber = page == null ? DEFAULT_PAGE : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return PageRequest.of(pageNumber, pageSize);
    }
}
