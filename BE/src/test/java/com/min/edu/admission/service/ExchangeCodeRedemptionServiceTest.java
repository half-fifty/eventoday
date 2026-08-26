package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.dto.ExchangeCodeDtos;
import com.min.edu.admission.dto.ExchangeCodeRedemptionDtos;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.admission.support.AdmissionQrTokenGenerator;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.payment.service.GuestOrderAccessService;
import com.min.edu.payment.service.GuestTicketOrderAccess;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeCodeRedemptionServiceTest {

    @Mock
    private ExchangeCodeRepository exchangeCodeRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private AdmissionTicketRepository admissionTicketRepository;

    @Mock
    private AdmissionQrTokenGenerator admissionQrTokenGenerator;

    @Mock
    private GuestOrderAccessService guestOrderAccessService;

    private ExchangeCodeRedemptionService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeCodeRedemptionService(
            exchangeCodeRepository,
            eventRepository,
            admissionTicketRepository,
            admissionQrTokenGenerator,
            guestOrderAccessService
        );
    }

    @Test
    void validate_succeedsForIssuedTicketOrderCodeOwnedByActor() {
        ExchangeCode exchangeCode = ticketCode(ExchangeCodeStatus.ISSUED, 10L, null);
        given(exchangeCodeRepository.findByCode("CODE-1")).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));

        ExchangeCodeRedemptionDtos.ValidationResponse response =
            service.validate(request("CODE-1"), actor(10L));

        assertThat(response.valid()).isTrue();
        assertThat(response.eventId()).isEqualTo(1L);
        assertThat(response.source()).isEqualTo(ExchangeCodeDtos.Source.TICKET_ORDER);
        assertThat(response.status()).isEqualTo(ExchangeCodeStatus.ISSUED);
        verifyNoInteractions(admissionQrTokenGenerator);
    }

    @Test
    void validate_failsWhenUnauthenticatedBeforeRepositoryLookup() {
        assertThatThrownBy(() -> service.validate(request("CODE-1"), null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

        verifyNoInteractions(exchangeCodeRepository);
    }

    @Test
    void validate_failsWhenCodeIsBlank() {
        assertThatThrownBy(() -> service.validate(request(" "), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(exchangeCodeRepository);
    }

    @Test
    void validate_failsWhenCodeDoesNotExist() {
        given(exchangeCodeRepository.findByCode("NOPE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate(request("NOPE"), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND);
    }

    @Test
    void validate_failsForNonIssuedStatuses() {
        assertInvalidStatus(ExchangeCodeStatus.REDEEMED);
        assertInvalidStatus(ExchangeCodeStatus.CANCELLED);
        assertInvalidStatus(ExchangeCodeStatus.EXPIRED);
    }

    @Test
    void validate_failsWhenExchangeCodeExpired() {
        ExchangeCode exchangeCode = ticketCode(
            ExchangeCodeStatus.ISSUED,
            10L,
            OffsetDateTime.now().minusSeconds(1)
        );
        given(exchangeCodeRepository.findByCode("CODE-1")).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));

        assertThatThrownBy(() -> service.validate(request("CODE-1"), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_EXPIRED);
    }

    @Test
    void validate_allowsNullExpiresAt() {
        ExchangeCode exchangeCode = ticketCode(ExchangeCodeStatus.ISSUED, 10L, null);
        given(exchangeCodeRepository.findByCode("CODE-1")).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));

        ExchangeCodeRedemptionDtos.ValidationResponse response =
            service.validate(request("CODE-1"), actor(10L));

        assertThat(response.valid()).isTrue();
    }

    @Test
    void validate_failsWhenEventEndedOrNotPublished() {
        ExchangeCode endedEventCode = ticketCode(ExchangeCodeStatus.ISSUED, 10L, null);
        given(exchangeCodeRepository.findByCode("ENDED")).willReturn(Optional.of(endedEventCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, -1)));

        assertThatThrownBy(() -> service.validate(request("ENDED"), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_EVENT_NOT_REDEEMABLE);

        ExchangeCode suspendedEventCode = ticketCode(ExchangeCodeStatus.ISSUED, 10L, null);
        given(exchangeCodeRepository.findByCode("SUSPENDED"))
            .willReturn(Optional.of(suspendedEventCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.SUSPENDED, 1)));

        assertThatThrownBy(() -> service.validate(request("SUSPENDED"), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_EVENT_NOT_REDEEMABLE);
    }

    @Test
    void validate_enforcesHolderPolicy() {
        assertHolderFailure(
            ticketCode(ExchangeCodeStatus.ISSUED, 99L, null),
            GlobalErrorCode.EXCHANGE_CODE_HOLDER_MISMATCH
        );
        assertHolderFailure(
            ticketCode(ExchangeCodeStatus.ISSUED, null, null),
            GlobalErrorCode.EXCHANGE_CODE_GUEST_NOT_REDEEMABLE
        );
        assertHolderFailure(
            externalCode(ExchangeCodeStatus.ISSUED, 99L, null),
            GlobalErrorCode.EXCHANGE_CODE_HOLDER_MISMATCH
        );
    }

    @Test
    void validate_allowsUnassignedExternalRequestCode() {
        ExchangeCode exchangeCode = externalCode(ExchangeCodeStatus.ISSUED, null, null);
        given(exchangeCodeRepository.findByCode("CODE-1")).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));

        ExchangeCodeRedemptionDtos.ValidationResponse response =
            service.validate(request("CODE-1"), actor(10L));

        assertThat(response.valid()).isTrue();
        assertThat(response.source()).isEqualTo(ExchangeCodeDtos.Source.EXTERNAL_REQUEST);
    }

    @Test
    void validate_failsWhenExchangeCodeHasNoSource() {
        ExchangeCode exchangeCode = exchangeCode(7L, 1L, null, null, null, ExchangeCodeStatus.ISSUED, null);
        given(exchangeCodeRepository.findByCode("CODE-1")).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));

        assertThatThrownBy(() -> service.validate(request("CODE-1"), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_INVALID_STATE);
    }

    @Test
    void redeem_assignsHolderRedeemsCodeAndCreatesAdmissionTicket() {
        ExchangeCode exchangeCode = externalCode(ExchangeCodeStatus.ISSUED, null, null);
        AdmissionTicket savedTicket = AdmissionTicket.builder()
            .id(11L)
            .exchangeCodeId(7L)
            .memberId(10L)
            .qrToken("qr-token")
            .status(AdmissionTicketStatus.ISSUED)
            .issuedAt(OffsetDateTime.now())
            .build();
        given(exchangeCodeRepository.findByCodeForUpdate("CODE-1")).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));
        given(admissionQrTokenGenerator.generate()).willReturn("qr-token");
        given(admissionTicketRepository.saveAndFlush(any())).willReturn(savedTicket);

        ExchangeCodeRedemptionDtos.RedemptionResponse response =
            service.redeem(request("CODE-1"), actor(10L));

        assertThat(exchangeCode.getHolderMemberId()).isEqualTo(10L);
        assertThat(exchangeCode.getStatus()).isEqualTo(ExchangeCodeStatus.REDEEMED);
        assertThat(exchangeCode.getRedeemedAt()).isNotNull();
        assertThat(response.exchangeCodeId()).isEqualTo(7L);
        assertThat(response.exchangeCodeStatus()).isEqualTo(ExchangeCodeStatus.REDEEMED);
        assertThat(response.admissionTicketId()).isEqualTo(11L);
        assertThat(response.qrAvailable()).isTrue();

        ArgumentCaptor<AdmissionTicket> ticketCaptor =
            ArgumentCaptor.forClass(AdmissionTicket.class);
        verify(admissionTicketRepository).saveAndFlush(ticketCaptor.capture());
        AdmissionTicket admissionTicket = ticketCaptor.getValue();
        assertThat(admissionTicket.getExchangeCodeId()).isEqualTo(7L);
        assertThat(admissionTicket.getMemberId()).isEqualTo(10L);
        assertThat(admissionTicket.getQrToken()).isEqualTo("qr-token");
        assertThat(admissionTicket.getStatus()).isEqualTo(AdmissionTicketStatus.ISSUED);
        assertThat(admissionTicket.getUsedAt()).isNull();
        assertThat(admissionTicket.getCancelledAt()).isNull();
    }

    @Test
    void redeem_failsWhenAdmissionTicketAlreadyExists() {
        ExchangeCode exchangeCode = ticketCode(ExchangeCodeStatus.ISSUED, 10L, null);
        given(exchangeCodeRepository.findByCodeForUpdate("CODE-1")).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));
        given(admissionTicketRepository.existsByExchangeCodeId(7L)).willReturn(true);

        assertThatThrownBy(() -> service.redeem(request("CODE-1"), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_ALREADY_EXISTS);

        verify(admissionQrTokenGenerator, never()).generate();
        verify(admissionTicketRepository, never()).saveAndFlush(any());
    }

    @Test
    void redeem_completedReplayReturnsExistingAdmissionTicketWithoutNewQrToken() {
        ExchangeCode exchangeCode = ticketCode(ExchangeCodeStatus.REDEEMED, 10L, null);
        AdmissionTicket existingTicket = admissionTicket(11L, 7L, 10L, "existing-qr-token",
            AdmissionTicketStatus.USED);
        given(exchangeCodeRepository.findByCodeForUpdate("CODE-1")).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, -1)));
        given(admissionTicketRepository.findByExchangeCodeId(7L)).willReturn(Optional.of(existingTicket));

        ExchangeCodeRedemptionDtos.RedemptionResponse response =
            service.redeem(request("CODE-1"), actor(10L));

        assertThat(response.exchangeCodeId()).isEqualTo(7L);
        assertThat(response.exchangeCodeStatus()).isEqualTo(ExchangeCodeStatus.REDEEMED);
        assertThat(response.admissionTicketId()).isEqualTo(11L);
        assertThat(response.admissionTicketStatus()).isEqualTo(AdmissionTicketStatus.USED);
        assertThat(response.qrAvailable()).isFalse();
        verify(admissionQrTokenGenerator, never()).generate();
        verify(admissionTicketRepository, never()).saveAndFlush(any());
    }

    @Test
    void redeem_completedReplayRejectsDifferentMemberBeforeReturningTicket() {
        ExchangeCode exchangeCode = ticketCode(ExchangeCodeStatus.REDEEMED, 99L, null);
        given(exchangeCodeRepository.findByCodeForUpdate("CODE-1")).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));

        assertThatThrownBy(() -> service.redeem(request("CODE-1"), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_HOLDER_MISMATCH);

        verify(admissionTicketRepository, never()).findByExchangeCodeId(any());
        verify(admissionQrTokenGenerator, never()).generate();
    }

    @Test
    void redeem_completedReplayFailsWhenTicketMissingWithoutCreatingNewTicket() {
        ExchangeCode exchangeCode = ticketCode(ExchangeCodeStatus.REDEEMED, 10L, null);
        given(exchangeCodeRepository.findByCodeForUpdate("CODE-1")).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));
        given(admissionTicketRepository.findByExchangeCodeId(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem(request("CODE-1"), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_DATA_INCONSISTENT);

        verify(admissionQrTokenGenerator, never()).generate();
        verify(admissionTicketRepository, never()).saveAndFlush(any());
    }

    @Test
    void redeemGuestOrderExchangeCode_createsAdmissionTicketWithNullMemberId() {
        ExchangeCode exchangeCode = ticketCode(ExchangeCodeStatus.ISSUED, null, null);
        AdmissionTicket savedTicket = AdmissionTicket.builder()
            .id(21L)
            .exchangeCodeId(7L)
            .memberId(null)
            .qrToken("guest-qr-token")
            .status(AdmissionTicketStatus.ISSUED)
            .issuedAt(OffsetDateTime.now())
            .build();
        given(guestOrderAccessService.validateGuestTicketOrderAccess("ORDER-1", "guest-token"))
            .willReturn(new GuestTicketOrderAccess(3L, 1L, "ORDER-1"));
        given(exchangeCodeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));
        given(admissionQrTokenGenerator.generate()).willReturn("guest-qr-token");
        given(admissionTicketRepository.saveAndFlush(any())).willReturn(savedTicket);

        ExchangeCodeRedemptionDtos.RedemptionResponse response =
            service.redeemGuestOrderExchangeCode("ORDER-1", "guest-token", 7L);

        assertThat(response.admissionTicketId()).isEqualTo(21L);
        assertThat(response.qrAvailable()).isTrue();
        assertThat(exchangeCode.getStatus()).isEqualTo(ExchangeCodeStatus.REDEEMED);

        ArgumentCaptor<AdmissionTicket> ticketCaptor =
            ArgumentCaptor.forClass(AdmissionTicket.class);
        verify(admissionTicketRepository).saveAndFlush(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getMemberId()).isNull();
        assertThat(ticketCaptor.getValue().getExchangeCodeId()).isEqualTo(7L);
    }

    @Test
    void redeemGuestOrderExchangeCode_completedReplayReturnsExistingTicketInOrderScope() {
        ExchangeCode exchangeCode = ticketCode(ExchangeCodeStatus.REDEEMED, null, null);
        AdmissionTicket existingTicket = admissionTicket(21L, 7L, null, "guest-qr-token",
            AdmissionTicketStatus.USED);
        given(guestOrderAccessService.validateGuestTicketOrderAccess("ORDER-1", "guest-token"))
            .willReturn(new GuestTicketOrderAccess(3L, 1L, "ORDER-1"));
        given(exchangeCodeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, -1)));
        given(admissionTicketRepository.findByExchangeCodeId(7L)).willReturn(Optional.of(existingTicket));

        ExchangeCodeRedemptionDtos.RedemptionResponse response =
            service.redeemGuestOrderExchangeCode("ORDER-1", "guest-token", 7L);

        assertThat(response.admissionTicketId()).isEqualTo(21L);
        assertThat(response.admissionTicketStatus()).isEqualTo(AdmissionTicketStatus.USED);
        assertThat(response.qrAvailable()).isFalse();
        verify(admissionQrTokenGenerator, never()).generate();
        verify(admissionTicketRepository, never()).saveAndFlush(any());
    }

    @Test
    void redeemGuestOrderExchangeCode_completedReplayRejectsOtherOrderBeforeReturningTicket() {
        ExchangeCode exchangeCode = exchangeCode(7L, 1L, null, 99L, null,
            ExchangeCodeStatus.REDEEMED, null);
        given(guestOrderAccessService.validateGuestTicketOrderAccess("ORDER-1", "guest-token"))
            .willReturn(new GuestTicketOrderAccess(3L, 1L, "ORDER-1"));
        given(exchangeCodeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));

        assertThatThrownBy(() -> service.redeemGuestOrderExchangeCode("ORDER-1", "guest-token", 7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_DENIED);

        verify(admissionTicketRepository, never()).findByExchangeCodeId(any());
        verify(admissionQrTokenGenerator, never()).generate();
    }

    @Test
    void redeemGuestOrderExchangeCode_rejectsOtherOrderCodeAndDuplicateRedemption() {
        ExchangeCode otherOrderCode = exchangeCode(8L, 1L, null, 99L, null, ExchangeCodeStatus.ISSUED, null);
        given(guestOrderAccessService.validateGuestTicketOrderAccess("ORDER-1", "guest-token"))
            .willReturn(new GuestTicketOrderAccess(3L, 1L, "ORDER-1"));
        given(exchangeCodeRepository.findByIdForUpdate(8L)).willReturn(Optional.of(otherOrderCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));

        assertThatThrownBy(() -> service.redeemGuestOrderExchangeCode("ORDER-1", "guest-token", 8L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_DENIED);

        ExchangeCode redeemedCode = ticketCode(ExchangeCodeStatus.ISSUED, null, null);
        given(exchangeCodeRepository.findByIdForUpdate(7L)).willReturn(Optional.of(redeemedCode));
        given(admissionTicketRepository.existsByExchangeCodeId(7L)).willReturn(true);

        assertThatThrownBy(() -> service.redeemGuestOrderExchangeCode("ORDER-1", "guest-token", 7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_TICKET_ALREADY_EXISTS);
    }

    private void assertInvalidStatus(ExchangeCodeStatus status) {
        ExchangeCode exchangeCode = ticketCode(status, 10L, null);
        given(exchangeCodeRepository.findByCode(status.name())).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));

        assertThatThrownBy(() -> service.validate(request(status.name()), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_INVALID_STATE);
    }

    private void assertHolderFailure(ExchangeCode exchangeCode, GlobalErrorCode expected) {
        given(exchangeCodeRepository.findByCode(expected.name())).willReturn(Optional.of(exchangeCode));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event(EventStatus.PUBLISHED, 1)));

        assertThatThrownBy(() -> service.validate(request(expected.name()), actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(expected);
    }

    private ExchangeCodeRedemptionDtos.Request request(String code) {
        return new ExchangeCodeRedemptionDtos.Request(code);
    }

    private AuthenticatedMemberDto actor(Long memberId) {
        return new AuthenticatedMemberDto(memberId, PlatformRole.USER);
    }

    private ExchangeCode ticketCode(
            ExchangeCodeStatus status,
            Long holderMemberId,
            OffsetDateTime expiresAt) {
        return exchangeCode(7L, 1L, null, 3L, holderMemberId, status, expiresAt);
    }

    private ExchangeCode externalCode(
            ExchangeCodeStatus status,
            Long holderMemberId,
            OffsetDateTime expiresAt) {
        return exchangeCode(7L, 1L, 5L, null, holderMemberId, status, expiresAt);
    }

    private AdmissionTicket admissionTicket(
            Long id,
            Long exchangeCodeId,
            Long memberId,
            String qrToken,
            AdmissionTicketStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        return AdmissionTicket.builder()
            .id(id)
            .exchangeCodeId(exchangeCodeId)
            .memberId(memberId)
            .qrToken(qrToken)
            .status(status)
            .issuedAt(now.minusMinutes(5))
            .usedAt(status == AdmissionTicketStatus.USED ? now : null)
            .build();
    }

    private ExchangeCode exchangeCode(
            Long id,
            Long eventId,
            Long exchangeCodeRequestId,
            Long ticketOrderId,
            Long holderMemberId,
            ExchangeCodeStatus status,
            OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now();
        return ExchangeCode.builder()
            .id(id)
            .eventId(eventId)
            .exchangeCodeRequestId(exchangeCodeRequestId)
            .ticketOrderId(ticketOrderId)
            .holderMemberId(holderMemberId)
            .code("CODE-1")
            .status(status)
            .expiresAt(expiresAt)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private Event event(EventStatus status, int endAtOffsetDays) {
        OffsetDateTime now = OffsetDateTime.now();
        return Event.builder()
            .id(1L)
            .organizerOrganizationId(100L)
            .name("event")
            .eventType("CONFERENCE")
            .description("description")
            .venueName("venue")
            .address("address")
            .startAt(now.minusDays(1))
            .endAt(now.plusDays(endAtOffsetDays))
            .ticketPrice(BigDecimal.ZERO)
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(0)
            .ticketPurchaseLimit(5)
            .status(status)
            .boothRecruitmentEnabled(false)
            .venueMapEnabled(false)
            .boothReservationEnabled(false)
            .noShowGraceMinutes(10)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
