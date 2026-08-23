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
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdmissionEligibilityQueryService {

    private final AdmissionTicketRepository admissionTicketRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final EventRepository eventRepository;
    private final AdmissionEligibilityPolicy admissionEligibilityPolicy;

    public AdmissionEligibilityQueryService(
            AdmissionTicketRepository admissionTicketRepository,
            ExchangeCodeRepository exchangeCodeRepository,
            EventRepository eventRepository,
            AdmissionEligibilityPolicy admissionEligibilityPolicy) {
        this.admissionTicketRepository = admissionTicketRepository;
        this.exchangeCodeRepository = exchangeCodeRepository;
        this.eventRepository = eventRepository;
        this.admissionEligibilityPolicy = admissionEligibilityPolicy;
    }

    public AdmissionEligibilityResult evaluateByTicketId(Long eventId, Long admissionTicketId) {
        AdmissionTicket ticket = admissionTicketRepository.findByIdAndEventId(
                admissionTicketId,
                eventId
            )
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
        ExchangeCode exchangeCode = exchangeCodeRepository.findById(ticket.getExchangeCodeId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND));
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
        return admissionEligibilityPolicy.evaluate(
            eventId,
            event,
            ticket,
            exchangeCode,
            OffsetDateTime.now()
        );
    }

    public Long resolveTicketIdByQrToken(Long eventId, String qrToken) {
        if (qrToken == null || qrToken.isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        AdmissionTicket ticket = admissionTicketRepository.findByQrToken(qrToken.trim())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
        ExchangeCode exchangeCode = exchangeCodeRepository.findById(ticket.getExchangeCodeId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND));
        if (!eventId.equals(exchangeCode.getEventId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return ticket.getId();
    }
}
