package com.min.edu.admission.service;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.policy.AdmissionEligibilityPolicy;
import com.min.edu.admission.policy.AdmissionEligibilityResult;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.payment.service.GuestOrderAccessService;
import com.min.edu.payment.service.GuestTicketOrderAccess;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdmissionFailureEligibilityQueryService {

    private final AdmissionTicketRepository admissionTicketRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final EventRepository eventRepository;
    private final GuestOrderAccessService guestOrderAccessService;
    private final AdmissionEligibilityPolicy admissionEligibilityPolicy;

    public AdmissionFailureEligibilityQueryService(
            AdmissionTicketRepository admissionTicketRepository,
            ExchangeCodeRepository exchangeCodeRepository,
            EventRepository eventRepository,
            GuestOrderAccessService guestOrderAccessService,
            AdmissionEligibilityPolicy admissionEligibilityPolicy) {
        this.admissionTicketRepository = admissionTicketRepository;
        this.exchangeCodeRepository = exchangeCodeRepository;
        this.eventRepository = eventRepository;
        this.guestOrderAccessService = guestOrderAccessService;
        this.admissionEligibilityPolicy = admissionEligibilityPolicy;
    }

    public AdmissionFailureEligibilityView evaluateForMember(
            Long memberId,
            Long admissionTicketId) {
        if (memberId == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        AdmissionTicket ticket = findTicket(admissionTicketId);
        if (!memberId.equals(ticket.getMemberId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return evaluate(ticket);
    }

    public AdmissionFailureEligibilityView evaluateForGuest(
            String orderNo,
            String orderAccessToken,
            Long admissionTicketId) {
        GuestTicketOrderAccess access =
            guestOrderAccessService.validateGuestTicketOrderAccess(orderNo, orderAccessToken);
        AdmissionTicket ticket = admissionTicketRepository
            .findByIdAndTicketOrderId(admissionTicketId, access.ticketOrderId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
        return evaluate(ticket);
    }

    private AdmissionFailureEligibilityView evaluate(AdmissionTicket ticket) {
        ExchangeCode exchangeCode = exchangeCodeRepository.findById(ticket.getExchangeCodeId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND));
        Event event = eventRepository.findById(exchangeCode.getEventId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
        AdmissionEligibilityResult eligibility = admissionEligibilityPolicy.evaluate(
            exchangeCode.getEventId(),
            event,
            ticket,
            exchangeCode,
            OffsetDateTime.now()
        );
        return new AdmissionFailureEligibilityView(
            ticket.getId(),
            exchangeCode.getEventId(),
            event.getName(),
            eligibility
        );
    }

    private AdmissionTicket findTicket(Long admissionTicketId) {
        return admissionTicketRepository.findById(admissionTicketId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
    }
}
