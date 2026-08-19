package com.min.edu.admission.service;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.dto.AdmissionTicketView;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.service.EventOperationAccessService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdmissionTicketOperationQueryService {

    private final AdmissionTicketRepository admissionTicketRepository;
    private final EventOperationAccessService eventOperationAccessService;

    public AdmissionTicketOperationView getAdmissionTicketStatus(
            Long eventId,
            Long memberId,
            Long admissionTicketId) {
        eventOperationAccessService.requireOperationalAccess(eventId, memberId);
        if (admissionTicketId == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        AdmissionTicketView ticket = admissionTicketRepository
            .findAdmissionTicketDetail(admissionTicketId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
        if (!eventId.equals(ticket.getEventId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        return AdmissionTicketOperationView.from(ticket);
    }

    public record AdmissionTicketOperationView(
            Long admissionTicketId,
            Long eventId,
            String eventName,
            AdmissionTicketStatus status,
            boolean qrAvailable,
            OffsetDateTime issuedAt,
            OffsetDateTime usedAt,
            OffsetDateTime cancelledAt,
            ExchangeCodeStatus exchangeCodeStatus) {

        static AdmissionTicketOperationView from(AdmissionTicketView ticket) {
            return new AdmissionTicketOperationView(
                ticket.getAdmissionTicketId(),
                ticket.getEventId(),
                ticket.getEventName(),
                ticket.getStatus(),
                ticket.getStatus() == AdmissionTicketStatus.ISSUED && ticket.getQrToken() != null,
                ticket.getIssuedAt(),
                ticket.getUsedAt(),
                ticket.getCancelledAt(),
                ticket.getExchangeCodeStatus()
            );
        }
    }
}
