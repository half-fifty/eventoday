package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.policy.AdmissionEligibilityPolicy;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.repository.EventRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdmissionEligibilityQueryServiceTest {

    private final AdmissionTicketRepository admissionTicketRepository =
        Mockito.mock(AdmissionTicketRepository.class);
    private final ExchangeCodeRepository exchangeCodeRepository =
        Mockito.mock(ExchangeCodeRepository.class);
    private final EventRepository eventRepository = Mockito.mock(EventRepository.class);
    private final AdmissionEligibilityPolicy admissionEligibilityPolicy =
        Mockito.mock(AdmissionEligibilityPolicy.class);
    private final AdmissionEligibilityQueryService service = new AdmissionEligibilityQueryService(
        admissionTicketRepository,
        exchangeCodeRepository,
        eventRepository,
        admissionEligibilityPolicy
    );

    @Test
    void resolvesQrTokenToTicketIdOnlyInsideRequestedEvent() {
        given(admissionTicketRepository.findByQrToken("secret-qr-token"))
            .willReturn(Optional.of(ticket()));
        given(exchangeCodeRepository.findById(20L)).willReturn(Optional.of(exchangeCode(100L)));

        Long ticketId = service.resolveTicketIdByQrToken(100L, " secret-qr-token ");

        assertThat(ticketId).isEqualTo(11L);
    }

    @Test
    void rejectsQrTokenFromAnotherEventBeforePromptContextIsBuilt() {
        given(admissionTicketRepository.findByQrToken("secret-qr-token"))
            .willReturn(Optional.of(ticket()));
        given(exchangeCodeRepository.findById(20L)).willReturn(Optional.of(exchangeCode(200L)));

        assertThatThrownBy(() -> service.resolveTicketIdByQrToken(100L, "secret-qr-token"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    private AdmissionTicket ticket() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        return AdmissionTicket.builder()
            .id(11L)
            .exchangeCodeId(20L)
            .memberId(30L)
            .qrToken("secret-qr-token")
            .status(AdmissionTicketStatus.ISSUED)
            .issuedAt(now.minusHours(1))
            .build();
    }

    private ExchangeCode exchangeCode(Long eventId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        return ExchangeCode.builder()
            .id(20L)
            .eventId(eventId)
            .ticketOrderId(40L)
            .holderMemberId(30L)
            .code("EXCHANGE-CODE-SECRET")
            .status(ExchangeCodeStatus.REDEEMED)
            .expiresAt(now.plusDays(1))
            .redeemedAt(now.minusHours(1))
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusHours(1))
            .build();
    }
}
