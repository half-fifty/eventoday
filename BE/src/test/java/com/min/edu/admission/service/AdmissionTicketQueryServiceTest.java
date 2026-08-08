package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.dto.AdmissionTicketDtos;
import com.min.edu.admission.dto.AdmissionTicketView;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.support.AdmissionQrImageGenerator;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdmissionTicketQueryServiceTest {
    @Mock private AdmissionTicketRepository admissionTicketRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EventMemberRepository eventMemberRepository;
    @Mock private EventOrganizationMemberRepository organizationMemberRepository;
    @Mock private AdmissionQrImageGenerator admissionQrImageGenerator;

    private AdmissionTicketQueryService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionTicketQueryService(
            admissionTicketRepository,
            eventRepository,
            eventMemberRepository,
            organizationMemberRepository,
            admissionQrImageGenerator
        );
    }

    @Test
    void getMyAdmissionTickets_usesActorMemberIdAndPaging() {
        given(admissionTicketRepository.findMyAdmissionTickets(
            eq(10L),
            eq(AdmissionTicketStatus.ISSUED),
            any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(view(11L, 10L, AdmissionTicketStatus.ISSUED))));

        Page<AdmissionTicketDtos.MyListResponse> response = service.getMyAdmissionTickets(
            AdmissionTicketStatus.ISSUED,
            actor(10L),
            1,
            10
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().admissionTicketId()).isEqualTo(11L);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(admissionTicketRepository).findMyAdmissionTickets(
            eq(10L),
            eq(AdmissionTicketStatus.ISSUED),
            pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void getMyAdmissionTickets_failsWhenUnauthenticatedOrPageInvalid() {
        assertThatThrownBy(() -> service.getMyAdmissionTickets(null, null, 0, 20))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

        assertThatThrownBy(() -> service.getMyAdmissionTickets(null, actor(10L), -1, 20))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> service.getMyAdmissionTickets(null, actor(10L), 0, 101))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(admissionTicketRepository);
    }

    @Test
    void getMyAdmissionTicketDetail_allowsOwnerOnly() {
        given(admissionTicketRepository.findAdmissionTicketDetail(11L))
            .willReturn(Optional.of(view(11L, 10L, AdmissionTicketStatus.ISSUED)));

        AdmissionTicketDtos.DetailResponse response =
            service.getMyAdmissionTicketDetail(11L, actor(10L));

        assertThat(response.admissionTicketId()).isEqualTo(11L);
        assertThat(response.exchangeCodeStatus()).isEqualTo(ExchangeCodeStatus.REDEEMED);
        assertThat(response.qrAvailable()).isTrue();
    }

    @Test
    void getMyAdmissionTicketDetail_returnsQrAvailableOnlyForIssuedTicketWithQrToken() {
        assertDetailQrAvailable(AdmissionTicketStatus.ISSUED, "qr-token", true);
        assertDetailQrAvailable(AdmissionTicketStatus.ISSUED, null, false);
        assertDetailQrAvailable(AdmissionTicketStatus.USED, "qr-token", false);
        assertDetailQrAvailable(AdmissionTicketStatus.CANCELLED, "qr-token", false);
        assertDetailQrAvailable(AdmissionTicketStatus.EXPIRED, "qr-token", false);
    }

    @Test
    void getMyAdmissionTicketDetail_failsWhenNotFoundOrOtherMember() {
        given(admissionTicketRepository.findAdmissionTicketDetail(11L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyAdmissionTicketDetail(11L, actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND);

        given(admissionTicketRepository.findAdmissionTicketDetail(12L))
            .willReturn(Optional.of(view(12L, 99L, AdmissionTicketStatus.ISSUED)));

        assertThatThrownBy(() -> service.getMyAdmissionTicketDetail(12L, actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    @Test
    void getMyAdmissionTicketQr_allowsIssuedOwnerOnly() {
        AdmissionTicket ticket = ticket(11L, 10L, AdmissionTicketStatus.ISSUED);
        given(admissionTicketRepository.findById(11L)).willReturn(Optional.of(ticket));
        given(admissionQrImageGenerator.generate("qr-token")).willReturn(new byte[] {1, 2, 3});

        byte[] response = service.getMyAdmissionTicketQr(11L, actor(10L));

        assertThat(response).containsExactly(1, 2, 3);
        verify(admissionQrImageGenerator).generate("qr-token");
    }

    @Test
    void getMyAdmissionTicketQr_failsWhenIssuedTicketHasNoQrToken() {
        AdmissionTicket ticket = ticket(13L, 10L, AdmissionTicketStatus.ISSUED, null);
        given(admissionTicketRepository.findById(13L)).willReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.getMyAdmissionTicketQr(13L, actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_QR_NOT_AVAILABLE);

        verify(admissionQrImageGenerator, never()).generate(any());
    }

    @Test
    void getMyAdmissionTicketQr_failsWhenNotFoundOtherMemberOrUnavailableStatus() {
        given(admissionTicketRepository.findById(11L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyAdmissionTicketQr(11L, actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND);

        given(admissionTicketRepository.findById(12L))
            .willReturn(Optional.of(ticket(12L, 99L, AdmissionTicketStatus.ISSUED)));

        assertThatThrownBy(() -> service.getMyAdmissionTicketQr(12L, actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);

        assertQrUnavailable(AdmissionTicketStatus.USED);
        assertQrUnavailable(AdmissionTicketStatus.CANCELLED);
        assertQrUnavailable(AdmissionTicketStatus.EXPIRED);
    }

    @Test
    void getEventAdmissionTickets_allowsOrganizationManagerWithoutEventMemberLookup() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        given(admissionTicketRepository.findEventAdmissionTickets(
            eq(1L), eq(null), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(view(11L, 10L, AdmissionTicketStatus.ISSUED))));

        Page<AdmissionTicketDtos.EventListResponse> response =
            service.getEventAdmissionTickets(1L, null, actor(10L), 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().memberNickname()).isEqualTo("holder");
        verify(eventMemberRepository, never())
            .existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(any(), any(), any());
    }

    @Test
    void getEventAdmissionTickets_allowsActiveEventManager() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            1L, 10L, EventRole.EVENT_MANAGER))
            .willReturn(true);
        given(admissionTicketRepository.findEventAdmissionTickets(
            eq(1L), eq(AdmissionTicketStatus.USED), any(Pageable.class)))
            .willReturn(Page.empty());

        Page<AdmissionTicketDtos.EventListResponse> response =
            service.getEventAdmissionTickets(1L, AdmissionTicketStatus.USED, actor(10L), 0, 20);

        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void getEventAdmissionTickets_failsForNoEventNoAuthorityOrInvalidPaging() {
        given(eventRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventAdmissionTickets(1L, null, actor(10L), 0, 20))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EVENT_NOT_FOUND);

        Event event = event();
        given(eventRepository.findById(2L)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> service.getEventAdmissionTickets(2L, null, actor(10L), 0, 20))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);

        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);

        assertThatThrownBy(() -> service.getEventAdmissionTickets(2L, null, actor(10L), 0, 0))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
    }

    private void assertQrUnavailable(AdmissionTicketStatus status) {
        given(admissionTicketRepository.findById(status.ordinal() + 100L))
            .willReturn(Optional.of(ticket(status.ordinal() + 100L, 10L, status)));

        assertThatThrownBy(() -> service.getMyAdmissionTicketQr(status.ordinal() + 100L, actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_QR_NOT_AVAILABLE);
    }

    private void assertDetailQrAvailable(
            AdmissionTicketStatus status,
            String qrToken,
            boolean expected) {
        Long ticketId = 200L + status.ordinal() + (qrToken == null ? 10L : 0L);
        given(admissionTicketRepository.findAdmissionTicketDetail(ticketId))
            .willReturn(Optional.of(view(ticketId, 10L, status, qrToken)));

        AdmissionTicketDtos.DetailResponse response =
            service.getMyAdmissionTicketDetail(ticketId, actor(10L));

        assertThat(response.qrAvailable()).isEqualTo(expected);
    }

    private AuthenticatedMemberDto actor(Long memberId) {
        return new AuthenticatedMemberDto(memberId, PlatformRole.USER);
    }

    private AdmissionTicket ticket(Long id, Long memberId, AdmissionTicketStatus status) {
        return ticket(id, memberId, status, "qr-token");
    }

    private AdmissionTicket ticket(
            Long id,
            Long memberId,
            AdmissionTicketStatus status,
            String qrToken) {
        return AdmissionTicket.builder()
            .id(id)
            .exchangeCodeId(7L)
            .memberId(memberId)
            .qrToken(qrToken)
            .status(status)
            .issuedAt(OffsetDateTime.now())
            .build();
    }

    private AdmissionTicketView view(Long ticketId, Long memberId, AdmissionTicketStatus status) {
        return view(ticketId, memberId, status, "qr-token");
    }

    private AdmissionTicketView view(
            Long ticketId,
            Long memberId,
            AdmissionTicketStatus status,
            String qrToken) {
        OffsetDateTime now = OffsetDateTime.now();
        return new AdmissionTicketView() {
            @Override public Long getAdmissionTicketId() { return ticketId; }
            @Override public Long getEventId() { return 1L; }
            @Override public String getEventName() { return "event"; }
            @Override public Long getMemberId() { return memberId; }
            @Override public String getMemberNickname() { return "holder"; }
            @Override public AdmissionTicketStatus getStatus() { return status; }
            @Override public ExchangeCodeStatus getExchangeCodeStatus() { return ExchangeCodeStatus.REDEEMED; }
            @Override public OffsetDateTime getIssuedAt() { return now; }
            @Override public OffsetDateTime getUsedAt() { return null; }
            @Override public OffsetDateTime getCancelledAt() { return null; }
            @Override public String getQrToken() { return qrToken; }
        };
    }

    private Event event() {
        OffsetDateTime now = OffsetDateTime.now();
        return Event.builder()
            .id(1L)
            .organizerOrganizationId(100L)
            .name("event")
            .eventType("CONFERENCE")
            .description("description")
            .venueName("venue")
            .address("address")
            .startAt(now.plusDays(1))
            .endAt(now.plusDays(2))
            .ticketPrice(BigDecimal.ZERO)
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(0)
            .ticketPurchaseLimit(5)
            .status(EventStatus.PUBLISHED)
            .boothRecruitmentEnabled(false)
            .venueMapEnabled(false)
            .boothReservationEnabled(false)
            .noShowGraceMinutes(10)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
