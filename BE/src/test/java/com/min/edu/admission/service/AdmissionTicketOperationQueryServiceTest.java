package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.dto.AdmissionTicketView;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.service.EventOperationAccessService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdmissionTicketOperationQueryServiceTest {

    private final AdmissionTicketRepository admissionTicketRepository =
        Mockito.mock(AdmissionTicketRepository.class);
    private final EventOperationAccessService eventOperationAccessService =
        Mockito.mock(EventOperationAccessService.class);
    private final AdmissionTicketOperationQueryService service =
        new AdmissionTicketOperationQueryService(admissionTicketRepository, eventOperationAccessService);

    @Test
    void verifiesOperationalAccessAndReturnsTicketInCurrentEvent() {
        AdmissionTicketView view = view(100L);
        given(admissionTicketRepository.findAdmissionTicketDetail(11L)).willReturn(Optional.of(view));

        AdmissionTicketOperationQueryService.AdmissionTicketOperationView result =
            service.getAdmissionTicketStatus(100L, 10L, 11L);

        verify(eventOperationAccessService).requireOperationalAccess(100L, 10L);
        verify(admissionTicketRepository).findAdmissionTicketDetail(11L);
        assertThat(result.admissionTicketId()).isEqualTo(11L);
        assertThat(result.eventId()).isEqualTo(100L);
        assertThat(result.qrAvailable()).isTrue();
    }

    @Test
    void rejectsTicketFromAnotherEvent() {
        AdmissionTicketView view = view(200L);
        given(admissionTicketRepository.findAdmissionTicketDetail(11L)).willReturn(Optional.of(view));

        assertThatThrownBy(() -> service.getAdmissionTicketStatus(100L, 10L, 11L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    @Test
    void rejectsMissingTicket() {
        given(admissionTicketRepository.findAdmissionTicketDetail(11L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAdmissionTicketStatus(100L, 10L, 11L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND);
    }

    private AdmissionTicketView view(Long eventId) {
        AdmissionTicketView view = Mockito.mock(AdmissionTicketView.class);
        given(view.getAdmissionTicketId()).willReturn(11L);
        given(view.getEventId()).willReturn(eventId);
        given(view.getEventName()).willReturn("Eventoday Conference");
        given(view.getStatus()).willReturn(AdmissionTicketStatus.ISSUED);
        given(view.getExchangeCodeStatus()).willReturn(ExchangeCodeStatus.REDEEMED);
        given(view.getIssuedAt()).willReturn(OffsetDateTime.parse("2026-08-18T09:00:00+09:00"));
        given(view.getQrToken()).willReturn("secret-qr-token");
        return view;
    }
}
