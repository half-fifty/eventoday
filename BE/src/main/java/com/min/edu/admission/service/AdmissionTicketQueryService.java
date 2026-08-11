package com.min.edu.admission.service;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.dto.AdmissionTicketDtos;
import com.min.edu.admission.dto.AdmissionTicketView;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.support.AdmissionQrImageGenerator;
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
import com.min.edu.payment.service.GuestOrderAccessService;
import com.min.edu.payment.service.GuestTicketOrderAccess;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdmissionTicketQueryService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);

    private final AdmissionTicketRepository admissionTicketRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventOrganizationMemberRepository organizationMemberRepository;
    private final AdmissionQrImageGenerator admissionQrImageGenerator;
    private final GuestOrderAccessService guestOrderAccessService;

    public AdmissionTicketQueryService(
            AdmissionTicketRepository admissionTicketRepository,
            EventRepository eventRepository,
            EventMemberRepository eventMemberRepository,
            EventOrganizationMemberRepository organizationMemberRepository,
            AdmissionQrImageGenerator admissionQrImageGenerator,
            GuestOrderAccessService guestOrderAccessService) {
        this.admissionTicketRepository = admissionTicketRepository;
        this.eventRepository = eventRepository;
        this.eventMemberRepository = eventMemberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.admissionQrImageGenerator = admissionQrImageGenerator;
        this.guestOrderAccessService = guestOrderAccessService;
    }

    public Page<AdmissionTicketDtos.MyListResponse> getMyAdmissionTickets(
            AdmissionTicketStatus status,
            AuthenticatedMemberDto actor,
            Integer page,
            Integer size) {
        requireAuthenticated(actor);
        Pageable pageable = normalize(page, size);
        return admissionTicketRepository
            .findMyAdmissionTickets(actor.getMemberId(), status, pageable)
            .map(this::toMyListResponse);
    }

    public AdmissionTicketDtos.DetailResponse getMyAdmissionTicketDetail(
            Long admissionTicketId,
            AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        AdmissionTicketView view = findDetail(admissionTicketId);
        requireOwner(view, actor.getMemberId());
        return toDetailResponse(view);
    }

    public byte[] getMyAdmissionTicketQr(
            Long admissionTicketId,
            AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        AdmissionTicket ticket = admissionTicketRepository.findById(admissionTicketId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
        requireOwner(ticket, actor.getMemberId());
        if (ticket.getStatus() != AdmissionTicketStatus.ISSUED || ticket.getQrToken() == null) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_TICKET_QR_NOT_AVAILABLE);
        }
        return admissionQrImageGenerator.generate(ticket.getQrToken());
    }

    public List<AdmissionTicketDtos.MyListResponse> getGuestOrderAdmissionTickets(
            String orderNo,
            String orderAccessToken) {
        GuestTicketOrderAccess access =
            guestOrderAccessService.validateGuestTicketOrderAccess(orderNo, orderAccessToken);
        return admissionTicketRepository
            .findGuestOrderAdmissionTickets(access.ticketOrderId())
            .stream()
            .map(this::toMyListResponse)
            .toList();
    }

    public AdmissionTicketDtos.DetailResponse getGuestOrderAdmissionTicketDetail(
            String orderNo,
            String orderAccessToken,
            Long admissionTicketId) {
        GuestTicketOrderAccess access =
            guestOrderAccessService.validateGuestTicketOrderAccess(orderNo, orderAccessToken);
        AdmissionTicketView view = admissionTicketRepository
            .findGuestOrderAdmissionTicketDetail(access.ticketOrderId(), admissionTicketId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
        return toDetailResponse(view);
    }

    public byte[] getGuestOrderAdmissionTicketQr(
            String orderNo,
            String orderAccessToken,
            Long admissionTicketId) {
        GuestTicketOrderAccess access =
            guestOrderAccessService.validateGuestTicketOrderAccess(orderNo, orderAccessToken);
        AdmissionTicket ticket = admissionTicketRepository
            .findByIdAndTicketOrderId(admissionTicketId, access.ticketOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
        if (ticket.getStatus() != AdmissionTicketStatus.ISSUED || ticket.getQrToken() == null) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_TICKET_QR_NOT_AVAILABLE);
        }
        return admissionQrImageGenerator.generate(ticket.getQrToken());
    }

    public Page<AdmissionTicketDtos.EventListResponse> getEventAdmissionTickets(
            Long eventId,
            AdmissionTicketStatus status,
            AuthenticatedMemberDto actor,
            Integer page,
            Integer size) {
        requireAuthenticated(actor);
        Event event = findEvent(eventId);
        requireEventManager(event, actor);
        Pageable pageable = normalize(page, size);
        return admissionTicketRepository
            .findEventAdmissionTickets(eventId, status, pageable)
            .map(this::toEventListResponse);
    }

    private AdmissionTicketDtos.MyListResponse toMyListResponse(AdmissionTicketView view) {
        return new AdmissionTicketDtos.MyListResponse(
            view.getAdmissionTicketId(),
            view.getEventId(),
            view.getEventName(),
            view.getStatus(),
            view.getIssuedAt(),
            view.getUsedAt(),
            view.getCancelledAt()
        );
    }

    private AdmissionTicketDtos.DetailResponse toDetailResponse(AdmissionTicketView view) {
        return new AdmissionTicketDtos.DetailResponse(
            view.getAdmissionTicketId(),
            view.getEventId(),
            view.getEventName(),
            view.getExchangeCodeStatus(),
            view.getStatus(),
            view.getIssuedAt(),
            view.getUsedAt(),
            view.getCancelledAt(),
            view.getStatus() == AdmissionTicketStatus.ISSUED && view.getQrToken() != null
        );
    }

    private AdmissionTicketDtos.EventListResponse toEventListResponse(AdmissionTicketView view) {
        return new AdmissionTicketDtos.EventListResponse(
            view.getAdmissionTicketId(),
            view.getEventId(),
            view.getEventName(),
            view.getMemberNickname(),
            view.getStatus(),
            view.getIssuedAt(),
            view.getUsedAt(),
            view.getCancelledAt()
        );
    }

    private AdmissionTicketView findDetail(Long admissionTicketId) {
        return admissionTicketRepository.findAdmissionTicketDetail(admissionTicketId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
    }

    private void requireOwner(AdmissionTicketView view, Long memberId) {
        if (!memberId.equals(view.getMemberId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private void requireOwner(AdmissionTicket ticket, Long memberId) {
        if (!memberId.equals(ticket.getMemberId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private void requireEventManager(Event event, AuthenticatedMemberDto actor) {
        boolean organizerManager = organizationMemberRepository
            .existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                event.getOrganizerOrganizationId(),
                actor.getMemberId(),
                OrganizationMemberStatus.ACTIVE,
                MANAGER_ROLES
            );
        if (organizerManager) {
            return;
        }

        boolean assigned = eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            event.getId(),
            actor.getMemberId(),
            EventRole.EVENT_MANAGER
        );
        if (!assigned) {
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
