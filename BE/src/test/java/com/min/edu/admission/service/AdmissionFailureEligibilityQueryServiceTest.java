package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.policy.AdmissionEligibilityPolicy;
import com.min.edu.admission.policy.AdmissionEligibilityReasonCode;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.payment.service.GuestOrderAccessService;
import com.min.edu.payment.service.GuestTicketOrderAccess;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdmissionFailureEligibilityQueryServiceTest {

    private AdmissionTicketRepository admissionTicketRepository;
    private ExchangeCodeRepository exchangeCodeRepository;
    private EventRepository eventRepository;
    private GuestOrderAccessService guestOrderAccessService;
    private AdmissionEligibilityPolicy admissionEligibilityPolicy;
    private AdmissionFailureEligibilityQueryService service;

    @BeforeEach
    void setUp() {
        admissionTicketRepository = org.mockito.Mockito.mock(AdmissionTicketRepository.class);
        exchangeCodeRepository = org.mockito.Mockito.mock(ExchangeCodeRepository.class);
        eventRepository = org.mockito.Mockito.mock(EventRepository.class);
        guestOrderAccessService = org.mockito.Mockito.mock(GuestOrderAccessService.class);
        admissionEligibilityPolicy = org.mockito.Mockito.spy(new AdmissionEligibilityPolicy());
        service = new AdmissionFailureEligibilityQueryService(
            admissionTicketRepository,
            exchangeCodeRepository,
            eventRepository,
            guestOrderAccessService,
            admissionEligibilityPolicy
        );
    }

    @Test
    void evaluateForMemberAllowsOwnerAndUsesSharedAdmissionPolicy() {
        AdmissionTicket ticket = ticket(10L);
        given(admissionTicketRepository.findById(11L)).willReturn(Optional.of(ticket));
        given(exchangeCodeRepository.findById(1L)).willReturn(Optional.of(exchangeCode()));
        given(eventRepository.findById(3L)).willReturn(Optional.of(event()));

        AdmissionFailureEligibilityView view = service.evaluateForMember(10L, 11L);

        assertThat(view.eligibility().reasonCode())
            .isEqualTo(AdmissionEligibilityReasonCode.ELIGIBLE);
        verify(admissionEligibilityPolicy).evaluate(
            org.mockito.Mockito.eq(3L),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.eq(ticket),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.any()
        );
    }

    @Test
    void evaluateForMemberRejectsOtherMember() {
        given(admissionTicketRepository.findById(11L)).willReturn(Optional.of(ticket(10L)));

        assertThatThrownBy(() -> service.evaluateForMember(20L, 11L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    @Test
    void evaluateForGuestAllowsMatchingOrderTokenAndTicketOrder() {
        AdmissionTicket ticket = ticket(null);
        given(guestOrderAccessService.validateGuestTicketOrderAccess("ORDER-1", "token"))
            .willReturn(new GuestTicketOrderAccess(2L, 3L, "ORDER-1"));
        given(admissionTicketRepository.findByIdAndTicketOrderId(11L, 2L))
            .willReturn(Optional.of(ticket));
        given(exchangeCodeRepository.findById(1L)).willReturn(Optional.of(exchangeCode()));
        given(eventRepository.findById(3L)).willReturn(Optional.of(event()));

        AdmissionFailureEligibilityView view =
            service.evaluateForGuest("ORDER-1", "token", 11L);

        assertThat(view.admissionTicketId()).isEqualTo(11L);
    }

    @Test
    void evaluateForGuestRejectsTicketOutsideOrder() {
        given(guestOrderAccessService.validateGuestTicketOrderAccess("ORDER-1", "token"))
            .willReturn(new GuestTicketOrderAccess(2L, 3L, "ORDER-1"));
        given(admissionTicketRepository.findByIdAndTicketOrderId(11L, 2L))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluateForGuest("ORDER-1", "token", 11L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND);
    }

    private AdmissionTicket ticket(Long memberId) {
        return AdmissionTicket.builder()
            .id(11L)
            .exchangeCodeId(1L)
            .memberId(memberId)
            .qrToken("qr-token")
            .status(com.min.edu.admission.domain.AdmissionTicketStatus.ISSUED)
            .issuedAt(OffsetDateTime.now())
            .build();
    }

    private ExchangeCode exchangeCode() {
        return ExchangeCode.createForTicketOrder(
            3L,
            2L,
            10L,
            "code",
            null,
            OffsetDateTime.now()
        );
    }

    private Event event() {
        return Event.builder()
            .id(3L)
            .organizerOrganizationId(4L)
            .name("event")
            .eventType("FESTIVAL")
            .description("desc")
            .venueName("venue")
            .address("address")
            .startAt(OffsetDateTime.now().minusHours(1))
            .endAt(OffsetDateTime.now().plusHours(3))
            .ticketPrice(BigDecimal.valueOf(10000))
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(1)
            .ticketPurchaseLimit(2)
            .status(EventStatus.PUBLISHED)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
