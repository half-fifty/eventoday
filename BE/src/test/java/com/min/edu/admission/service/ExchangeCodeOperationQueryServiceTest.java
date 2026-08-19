package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.service.EventOperationAccessService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExchangeCodeOperationQueryServiceTest {

    private final ExchangeCodeRepository exchangeCodeRepository =
        Mockito.mock(ExchangeCodeRepository.class);
    private final AdmissionTicketRepository admissionTicketRepository =
        Mockito.mock(AdmissionTicketRepository.class);
    private final EventOperationAccessService eventOperationAccessService =
        Mockito.mock(EventOperationAccessService.class);
    private final ExchangeCodeOperationQueryService service = new ExchangeCodeOperationQueryService(
        exchangeCodeRepository,
        admissionTicketRepository,
        eventOperationAccessService
    );

    @Test
    void verifiesOperationalAccessCombinesTicketIssuedAndReturnsExchangeCodeInCurrentEvent() {
        given(exchangeCodeRepository.findById(20L)).willReturn(Optional.of(exchangeCode(100L)));
        given(admissionTicketRepository.existsByExchangeCodeId(20L)).willReturn(true);

        ExchangeCodeOperationQueryService.ExchangeCodeOperationView result =
            service.getExchangeCodeStatus(100L, 10L, 20L);

        verify(eventOperationAccessService).requireOperationalAccess(100L, 10L);
        verify(exchangeCodeRepository).findById(20L);
        verify(admissionTicketRepository).existsByExchangeCodeId(20L);
        assertThat(result.exchangeCodeId()).isEqualTo(20L);
        assertThat(result.maskedCode()).isEqualTo("ABCD****WXYZ");
        assertThat(result.admissionTicketIssued()).isTrue();
    }

    @Test
    void rejectsExchangeCodeFromAnotherEvent() {
        given(exchangeCodeRepository.findById(20L)).willReturn(Optional.of(exchangeCode(200L)));

        assertThatThrownBy(() -> service.getExchangeCodeStatus(100L, 10L, 20L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    @Test
    void rejectsMissingExchangeCode() {
        given(exchangeCodeRepository.findById(20L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExchangeCodeStatus(100L, 10L, 20L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND);
    }

    private ExchangeCode exchangeCode(Long eventId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        return ExchangeCode.builder()
            .id(20L)
            .eventId(eventId)
            .ticketOrderId(30L)
            .holderMemberId(40L)
            .code("ABCD1234WXYZ")
            .status(ExchangeCodeStatus.ISSUED)
            .expiresAt(now.plusDays(1))
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusDays(1))
            .build();
    }
}
