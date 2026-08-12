package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.min.edu.admission.domain.ExchangeCodeRequest;
import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import com.min.edu.admission.dto.ExchangeCodeRequestDtos;
import com.min.edu.admission.dto.ExchangeCodeRequestView;
import com.min.edu.admission.repository.ExchangeCodeRequestRepository;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.policy.EventOperationDeadlinePolicy;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ExchangeCodeRequestServiceTest {

    @Mock
    private ExchangeCodeRequestRepository exchangeCodeRequestRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventMemberRepository eventMemberRepository;

    @Mock
    private EventOrganizationMemberRepository organizationMemberRepository;

    @Mock
    private ExchangeCodeRequestExceptionTranslator exceptionTranslator;

    private ExchangeCodeRequestService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeCodeRequestService(
            exchangeCodeRequestRepository,
            eventRepository,
            eventMemberRepository,
            organizationMemberRepository,
            exceptionTranslator,
            new EventOperationDeadlinePolicy()
        );
    }

    @Test
    void createRequest_savesRequestedRequestForOrganizationOwner() {
        Event event = event(EventStatus.PUBLISHED, OffsetDateTime.now().plusDays(1));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        given(exchangeCodeRequestRepository.existsByEventIdAndStatus(
            1L, ExchangeCodeRequestStatus.REQUESTED))
            .willReturn(false);
        given(exchangeCodeRequestRepository.save(any(ExchangeCodeRequest.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        ExchangeCodeRequestDtos.CreateResponse response = service.createRequest(
            1L,
            new ExchangeCodeRequestDtos.CreateRequest(3, "external sales"),
            actor(10L, PlatformRole.USER)
        );

        assertThat(response.status()).isEqualTo(ExchangeCodeRequestStatus.REQUESTED);
        assertThat(response.requestedBy()).isEqualTo(10L);
        assertThat(response.requestedQuantity()).isEqualTo(3);

        ArgumentCaptor<ExchangeCodeRequest> captor =
            ArgumentCaptor.forClass(ExchangeCodeRequest.class);
        verify(exchangeCodeRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ExchangeCodeRequestStatus.REQUESTED);
        assertThat(captor.getValue().getPurpose()).isEqualTo("external sales");
    }

    @Test
    void createRequest_allowsActiveEventManager() {
        Event event = event(EventStatus.PUBLISHED, OffsetDateTime.now().plusDays(1));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            1L, 10L, EventRole.EVENT_MANAGER))
            .willReturn(true);
        given(exchangeCodeRequestRepository.save(any(ExchangeCodeRequest.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        service.createRequest(
            1L,
            new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
            actor(10L, PlatformRole.USER)
        );

        verify(exchangeCodeRequestRepository).save(any(ExchangeCodeRequest.class));
    }

    @Test
    void createRequest_failsWhenUnauthenticated() {
        assertThatThrownBy(() -> service.createRequest(
            1L,
            new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
            null
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void createRequest_failsUnauthorizedBeforeEventLookupWhenEventDoesNotExist() {
        assertThatThrownBy(() -> service.createRequest(
            999L,
            new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
            null
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void createRequest_failsWhenEventNotFound() {
        given(eventRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRequest(
            1L,
            new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
            actor(10L, PlatformRole.USER)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void createRequest_failsForOtherUser() {
        Event event = event(EventStatus.PUBLISHED, OffsetDateTime.now().plusDays(1));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> service.createRequest(
            1L,
            new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
            actor(10L, PlatformRole.USER)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);

        verify(exchangeCodeRequestRepository, never()).save(any());
    }

    @Test
    void createRequest_failsForCheckinStaff() {
        Event event = event(EventStatus.PUBLISHED, OffsetDateTime.now().plusDays(1));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            1L, 10L, EventRole.EVENT_MANAGER))
            .willReturn(false);

        assertThatThrownBy(() -> service.createRequest(
            1L,
            new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
            actor(10L, PlatformRole.USER)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);

        verify(exchangeCodeRequestRepository, never()).save(any());
    }

    @Test
    void createRequest_allowsAfterEventStartBeforeOperationCutoff() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = event(EventStatus.PUBLISHED, now.minusHours(1), now.plusHours(3));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        given(exchangeCodeRequestRepository.save(any(ExchangeCodeRequest.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        service.createRequest(
            1L,
            new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
            actor(10L, PlatformRole.USER)
        );

        verify(exchangeCodeRequestRepository).save(any(ExchangeCodeRequest.class));
    }

    @Test
    void createRequest_failsAtOperationCutoff() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = event(EventStatus.PUBLISHED, now.minusHours(2), now.plusHours(1));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);

        assertThatThrownBy(() -> service.createRequest(
            1L,
            new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
            actor(10L, PlatformRole.USER)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EVENT_ALREADY_STARTED);
    }

    @Test
    void createRequest_failsAfterOperationCutoff() {
        OffsetDateTime now = OffsetDateTime.now();
        Event event = event(EventStatus.PUBLISHED, now.minusHours(2), now.plusMinutes(30));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);

        assertThatThrownBy(() -> service.createRequest(
            1L,
            new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
            actor(10L, PlatformRole.USER)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EVENT_ALREADY_STARTED);
    }

    @Test
    void createRequest_failsWhenRequestedRequestAlreadyExists() {
        Event event = event(EventStatus.PUBLISHED, OffsetDateTime.now().plusDays(1));
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        given(exchangeCodeRequestRepository.existsByEventIdAndStatus(
            1L, ExchangeCodeRequestStatus.REQUESTED))
            .willReturn(true);

        assertThatThrownBy(() -> service.createRequest(
            1L,
            new ExchangeCodeRequestDtos.CreateRequest(1, "purpose"),
            actor(10L, PlatformRole.USER)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_EXISTS);
    }

    @Test
    void getEventRequests_requiresEventManagerAndPassesOptionalStatus() {
        Event event = event(EventStatus.PUBLISHED, OffsetDateTime.now().plusDays(1));
        PageRequest pageable = PageRequest.of(0, 20);
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        given(exchangeCodeRequestRepository.findEventRequests(
            1L, ExchangeCodeRequestStatus.APPROVED, pageable))
            .willReturn(new PageImpl<>(List.of(view(1L, 1L, ExchangeCodeRequestStatus.APPROVED)), pageable, 1));

        Page<ExchangeCodeRequestDtos.Response> page = service.getEventRequests(
            1L,
            ExchangeCodeRequestStatus.APPROVED,
            actor(10L, PlatformRole.USER),
            pageable
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).status()).isEqualTo(ExchangeCodeRequestStatus.APPROVED);
    }

    @Test
    void getEventRequests_failsUnauthorizedBeforeEventLookupWhenEventDoesNotExist() {
        assertThatThrownBy(() -> service.getEventRequests(
            999L,
            null,
            null,
            PageRequest.of(0, 20)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void getRequestDetail_allowsAdminAndEventManagerOnly() {
        given(exchangeCodeRequestRepository.findRequestDetail(1L))
            .willReturn(Optional.of(view(1L, 1L, ExchangeCodeRequestStatus.REQUESTED)));

        ExchangeCodeRequestDtos.Response adminResponse = service.getRequestDetail(
            1L,
            actor(99L, PlatformRole.PLATFORM_ADMIN)
        );

        assertThat(adminResponse.requestId()).isEqualTo(1L);
    }

    @Test
    void getRequestDetail_failsUnauthorizedBeforeRequestLookupWhenRequestDoesNotExist() {
        assertThatThrownBy(() -> service.getRequestDetail(999L, null))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
        verifyNoInteractions(exchangeCodeRequestRepository);
    }

    @Test
    void getRequestDetail_failsForOtherEventManager() {
        given(exchangeCodeRequestRepository.findRequestDetail(1L))
            .willReturn(Optional.of(view(1L, 1L, ExchangeCodeRequestStatus.REQUESTED)));
        given(eventRepository.findById(1L))
            .willReturn(Optional.of(event(EventStatus.PUBLISHED, OffsetDateTime.now().plusDays(1))));

        assertThatThrownBy(() -> service.getRequestDetail(1L, actor(10L, PlatformRole.USER)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    @Test
    void getAdminRequests_defaultsStatusToRequested() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(exchangeCodeRequestRepository.findAdminRequests(
            ExchangeCodeRequestStatus.REQUESTED, pageable))
            .willReturn(Page.empty(pageable));

        service.getAdminRequests(null, actor(99L, PlatformRole.PLATFORM_ADMIN), pageable);

        verify(exchangeCodeRequestRepository)
            .findAdminRequests(ExchangeCodeRequestStatus.REQUESTED, pageable);
    }

    @Test
    void getAdminRequests_failsForNormalUser() {
        assertThatThrownBy(() -> service.getAdminRequests(
            null,
            actor(10L, PlatformRole.USER),
            PageRequest.of(0, 20)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    @Test
    void approveRequest_changesRequestedToApproved() {
        ExchangeCodeRequest request = ExchangeCodeRequest.create(
            1L, 10L, 1, "purpose", OffsetDateTime.now());
        given(exchangeCodeRequestRepository.findByIdForUpdate(1L))
            .willReturn(Optional.of(request));

        service.approveRequest(1L, actor(99L, PlatformRole.PLATFORM_ADMIN));

        assertThat(request.getStatus()).isEqualTo(ExchangeCodeRequestStatus.APPROVED);
        assertThat(request.getReviewedBy()).isEqualTo(99L);
        assertThat(request.getReviewedAt()).isNotNull();
        assertThat(request.getRejectionReason()).isNull();
    }

    @Test
    void approveRequest_failsWhenAlreadyReviewed() {
        ExchangeCodeRequest request = ExchangeCodeRequest.create(
            1L, 10L, 1, "purpose", OffsetDateTime.now());
        request.approve(99L, OffsetDateTime.now());
        given(exchangeCodeRequestRepository.findByIdForUpdate(1L))
            .willReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approveRequest(
            1L,
            actor(99L, PlatformRole.PLATFORM_ADMIN)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);
    }

    @Test
    void rejectRequest_changesRequestedToRejected() {
        ExchangeCodeRequest request = ExchangeCodeRequest.create(
            1L, 10L, 1, "purpose", OffsetDateTime.now());
        given(exchangeCodeRequestRepository.findByIdForUpdate(1L))
            .willReturn(Optional.of(request));

        service.rejectRequest(1L, "not enough detail", actor(99L, PlatformRole.PLATFORM_ADMIN));

        assertThat(request.getStatus()).isEqualTo(ExchangeCodeRequestStatus.REJECTED);
        assertThat(request.getReviewedBy()).isEqualTo(99L);
        assertThat(request.getReviewedAt()).isNotNull();
        assertThat(request.getRejectionReason()).isEqualTo("not enough detail");
    }

    @Test
    void rejectRequest_failsWhenReasonIsInvalid() {
        AuthenticatedMemberDto admin = actor(99L, PlatformRole.PLATFORM_ADMIN);

        assertThatThrownBy(() -> service.rejectRequest(1L, null, admin))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> service.rejectRequest(1L, " ", admin))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> service.rejectRequest(1L, "a".repeat(2001), admin))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verify(exchangeCodeRequestRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectRequest_acceptsReasonWithTwoThousandCharacters() {
        ExchangeCodeRequest request = ExchangeCodeRequest.create(
            1L, 10L, 1, "purpose", OffsetDateTime.now());
        given(exchangeCodeRequestRepository.findByIdForUpdate(1L))
            .willReturn(Optional.of(request));
        String reason = "a".repeat(2000);

        service.rejectRequest(1L, reason, actor(99L, PlatformRole.PLATFORM_ADMIN));

        assertThat(request.getStatus()).isEqualTo(ExchangeCodeRequestStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo(reason);
    }

    @Test
    void rejectsPageSizeOverOneHundred() {
        assertThatThrownBy(() -> service.getAdminRequests(
            null,
            actor(99L, PlatformRole.PLATFORM_ADMIN),
            PageRequest.of(0, 101)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
    }

    private AuthenticatedMemberDto actor(Long memberId, PlatformRole platformRole) {
        return new AuthenticatedMemberDto(memberId, platformRole);
    }

    private Event event(EventStatus status, OffsetDateTime startAt) {
        return event(status, startAt, startAt.plusDays(1));
    }

    private Event event(EventStatus status, OffsetDateTime startAt, OffsetDateTime endAt) {
        return Event.builder()
            .id(1L)
            .organizerOrganizationId(100L)
            .name("event")
            .eventType("CONFERENCE")
            .description("description")
            .venueName("venue")
            .address("address")
            .startAt(startAt)
            .endAt(endAt)
            .ticketPrice(java.math.BigDecimal.ZERO)
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(0)
            .ticketPurchaseLimit(5)
            .status(status)
            .boothRecruitmentEnabled(false)
            .venueMapEnabled(false)
            .boothReservationEnabled(false)
            .noShowGraceMinutes(10)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private ExchangeCodeRequestView view(
            Long requestId,
            Long eventId,
            ExchangeCodeRequestStatus status) {
        return new ExchangeCodeRequestView() {
            @Override public Long getRequestId() { return requestId; }
            @Override public Long getEventId() { return eventId; }
            @Override public String getEventName() { return "event"; }
            @Override public Long getRequestedBy() { return 10L; }
            @Override public String getRequesterNickname() { return "requester"; }
            @Override public Integer getRequestedQuantity() { return 3; }
            @Override public String getPurpose() { return "purpose"; }
            @Override public ExchangeCodeRequestStatus getStatus() { return status; }
            @Override public Long getReviewedBy() { return null; }
            @Override public OffsetDateTime getReviewedAt() { return null; }
            @Override public String getRejectionReason() { return null; }
            @Override public OffsetDateTime getEmailedAt() { return null; }
            @Override public OffsetDateTime getCreatedAt() { return OffsetDateTime.now(); }
        };
    }
}
