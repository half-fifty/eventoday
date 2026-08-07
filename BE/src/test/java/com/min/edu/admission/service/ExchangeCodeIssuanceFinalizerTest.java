package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeRequest;
import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.admission.repository.ExchangeCodeRequestRepository;
import com.min.edu.admission.support.ExchangeCodeGenerator;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.MemberStatus;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.member.repository.MemberRepository;
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

@ExtendWith(MockitoExtension.class)
class ExchangeCodeIssuanceFinalizerTest {

    @Mock
    private ExchangeCodeRequestRepository exchangeCodeRequestRepository;

    @Mock
    private ExchangeCodeRepository exchangeCodeRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ExchangeCodeGenerator exchangeCodeGenerator;

    private ExchangeCodeIssuanceFinalizer finalizer;

    @BeforeEach
    void setUp() {
        finalizer = new ExchangeCodeIssuanceFinalizer(
            exchangeCodeRequestRepository,
            exchangeCodeRepository,
            eventRepository,
            memberRepository,
            exchangeCodeGenerator
        );
    }

    @Test
    void issue_createsCodesAndChangesRequestToIssued() {
        ExchangeCodeRequest request = request(ExchangeCodeRequestStatus.APPROVED, 2);
        Event event = event();
        given(exchangeCodeRequestRepository.findByIdForUpdate(7L)).willReturn(Optional.of(request));
        given(exchangeCodeRepository.countByExchangeCodeRequestId(7L)).willReturn(0L, 2L);
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(memberRepository.findById(10L)).willReturn(Optional.of(member("requester@example.com")));
        given(exchangeCodeGenerator.generate()).willReturn(
            "AAAAAA-AAAAAA-AAAAAA",
            "BBBBBB-BBBBBB-BBBBBB"
        );
        given(exchangeCodeRepository.saveAllAndFlush(any()))
            .willAnswer(invocation -> invocation.getArgument(0));

        ExchangeCodeIssuanceResult result = finalizer.issue(7L);

        assertThat(request.getStatus()).isEqualTo(ExchangeCodeRequestStatus.ISSUED);
        assertThat(result.generatedQuantity()).isEqualTo(2);
        assertThat(result.codes()).containsExactly(
            "AAAAAA-AAAAAA-AAAAAA",
            "BBBBBB-BBBBBB-BBBBBB"
        );
        assertThat(result.recipientEmail()).isEqualTo("requester@example.com");
        assertThat(result.expiresAt()).isEqualTo(event.getEndAt());

        ArgumentCaptor<List<ExchangeCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(exchangeCodeRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue())
            .allSatisfy(code -> {
                assertThat(code.getExchangeCodeRequestId()).isEqualTo(7L);
                assertThat(code.getTicketOrderId()).isNull();
                assertThat(code.getHolderMemberId()).isNull();
                assertThat(code.getExpiresAt()).isEqualTo(event.getEndAt());
            });
    }

    @Test
    void issue_failsWhenRequestNotFound() {
        given(exchangeCodeRequestRepository.findByIdForUpdate(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> finalizer.issue(7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_NOT_FOUND);
    }

    @Test
    void issue_failsWhenRequestIsNotApproved() {
        ExchangeCodeRequest request = request(ExchangeCodeRequestStatus.REQUESTED, 1);
        given(exchangeCodeRequestRepository.findByIdForUpdate(7L)).willReturn(Optional.of(request));
        given(exchangeCodeRepository.countByExchangeCodeRequestId(7L)).willReturn(0L);

        assertThatThrownBy(() -> finalizer.issue(7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);
    }

    @Test
    void issue_failsWhenRequestIsRejected() {
        ExchangeCodeRequest request = request(ExchangeCodeRequestStatus.REJECTED, 1);
        given(exchangeCodeRequestRepository.findByIdForUpdate(7L)).willReturn(Optional.of(request));
        given(exchangeCodeRepository.countByExchangeCodeRequestId(7L)).willReturn(0L);

        assertThatThrownBy(() -> finalizer.issue(7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);
    }

    @Test
    void issue_failsWhenRequestAlreadyIssued() {
        ExchangeCodeRequest request = request(ExchangeCodeRequestStatus.ISSUED, 2);
        given(exchangeCodeRequestRepository.findByIdForUpdate(7L)).willReturn(Optional.of(request));
        given(exchangeCodeRepository.countByExchangeCodeRequestId(7L)).willReturn(2L);

        assertThatThrownBy(() -> finalizer.issue(7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_ISSUED);
    }

    @Test
    void issue_failsWhenApprovedRequestAlreadyHasCodes() {
        ExchangeCodeRequest request = request(ExchangeCodeRequestStatus.APPROVED, 2);
        given(exchangeCodeRequestRepository.findByIdForUpdate(7L)).willReturn(Optional.of(request));
        given(exchangeCodeRepository.countByExchangeCodeRequestId(7L)).willReturn(1L);

        assertThatThrownBy(() -> finalizer.issue(7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ISSUANCE_INCONSISTENT);
    }

    @Test
    void issue_failsWhenRecipientIsMissing() {
        ExchangeCodeRequest request = request(ExchangeCodeRequestStatus.APPROVED, 1);
        given(exchangeCodeRequestRepository.findByIdForUpdate(7L)).willReturn(Optional.of(request));
        given(exchangeCodeRepository.countByExchangeCodeRequestId(7L)).willReturn(0L);
        given(eventRepository.findById(1L)).willReturn(Optional.of(event()));
        given(memberRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> finalizer.issue(7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_NOT_FOUND);
    }

    @Test
    void issue_failsWhenRecipientEmailIsBlank() {
        ExchangeCodeRequest request = request(ExchangeCodeRequestStatus.APPROVED, 1);
        given(exchangeCodeRequestRepository.findByIdForUpdate(7L)).willReturn(Optional.of(request));
        given(exchangeCodeRepository.countByExchangeCodeRequestId(7L)).willReturn(0L);
        given(eventRepository.findById(1L)).willReturn(Optional.of(event()));
        given(memberRepository.findById(10L)).willReturn(Optional.of(member(" ")));

        assertThatThrownBy(() -> finalizer.issue(7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_EMAIL_MISSING);
    }

    @Test
    void issue_failsWhenSavedCountDoesNotMatchRequestedQuantity() {
        ExchangeCodeRequest request = request(ExchangeCodeRequestStatus.APPROVED, 2);
        given(exchangeCodeRequestRepository.findByIdForUpdate(7L)).willReturn(Optional.of(request));
        given(exchangeCodeRepository.countByExchangeCodeRequestId(7L)).willReturn(0L, 1L);
        given(eventRepository.findById(1L)).willReturn(Optional.of(event()));
        given(memberRepository.findById(10L)).willReturn(Optional.of(member("requester@example.com")));
        given(exchangeCodeGenerator.generate()).willReturn(
            "AAAAAA-AAAAAA-AAAAAA",
            "BBBBBB-BBBBBB-BBBBBB"
        );
        given(exchangeCodeRepository.saveAllAndFlush(any()))
            .willAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> finalizer.issue(7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ISSUANCE_INCONSISTENT);
    }

    private ExchangeCodeRequest request(ExchangeCodeRequestStatus status, int quantity) {
        return ExchangeCodeRequest.builder()
            .id(7L)
            .eventId(1L)
            .requestedBy(10L)
            .requestedQuantity(quantity)
            .purpose("purpose")
            .status(status)
            .createdAt(OffsetDateTime.now())
            .build();
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

    private Member member(String email) {
        return Member.builder()
            .id(10L)
            .email(email)
            .nickname("requester")
            .platformRole(PlatformRole.USER)
            .status(MemberStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
