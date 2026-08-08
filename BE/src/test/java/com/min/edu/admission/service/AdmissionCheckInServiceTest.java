package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.min.edu.admission.domain.AdmissionAction;
import com.min.edu.admission.domain.AdmissionResult;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.dto.AdmissionCheckInDtos;
import com.min.edu.admission.dto.AdmissionLogView;
import com.min.edu.admission.repository.AdmissionLogRepository;
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
class AdmissionCheckInServiceTest {
    @Mock private EventRepository eventRepository;
    @Mock private EventMemberRepository eventMemberRepository;
    @Mock private EventOrganizationMemberRepository organizationMemberRepository;
    @Mock private AdmissionLogRepository admissionLogRepository;
    @Mock private AdmissionCheckInProcessor processor;

    private AdmissionCheckInService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionCheckInService(
            eventRepository,
            eventMemberRepository,
            organizationMemberRepository,
            admissionLogRepository,
            processor
        );
    }

    @Test
    void checkIn_allowsOwnerAndNormalizesBlankGateName() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        given(processor.checkIn(eq(1L), eq(event), eq("qr-token"), eq(null), eq(10L), any()))
            .willReturn(result(AdmissionCheckInProcessor.Outcome.SUCCESS));

        AdmissionCheckInDtos.CheckInResponse response =
            service.checkIn(1L, new AdmissionCheckInDtos.CheckInRequest("qr-token", "   "), actor(10L));

        assertThat(response.status()).isEqualTo(AdmissionTicketStatus.USED);
        assertThat(response.result()).isEqualTo(AdmissionResult.SUCCESS);
        verify(eventMemberRepository, never())
            .existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(any(), any(), any());
    }

    @Test
    void checkIn_allowsEventManagerAndCheckinStaff() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            1L, 10L, EventRole.EVENT_MANAGER))
            .willReturn(true);
        given(processor.checkIn(eq(1L), eq(event), eq("qr-token"), eq("A Gate"), eq(10L), any()))
            .willReturn(result(AdmissionCheckInProcessor.Outcome.SUCCESS));

        service.checkIn(1L, new AdmissionCheckInDtos.CheckInRequest("qr-token", "A Gate"), actor(10L));

        Event otherEvent = event(2L);
        given(eventRepository.findById(2L)).willReturn(Optional.of(otherEvent));
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            2L, 20L, EventRole.EVENT_MANAGER))
            .willReturn(false);
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            2L, 20L, EventRole.CHECKIN_STAFF))
            .willReturn(true);
        given(processor.checkIn(eq(2L), eq(otherEvent), eq("qr-token"), eq("A Gate"), eq(20L), any()))
            .willReturn(result(AdmissionCheckInProcessor.Outcome.SUCCESS));

        service.checkIn(2L, new AdmissionCheckInDtos.CheckInRequest("qr-token", "A Gate"), actor(20L));
    }

    @Test
    void checkIn_failsBeforeProcessorWhenUnauthenticatedEventMissingNoAuthorityOrInvalidRequest() {
        assertThatThrownBy(() -> service.checkIn(
            1L,
            new AdmissionCheckInDtos.CheckInRequest("qr-token", null),
            null
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

        given(eventRepository.findById(1L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.checkIn(
            1L,
            new AdmissionCheckInDtos.CheckInRequest("qr-token", null),
            actor(10L)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EVENT_NOT_FOUND);

        Event event = event(2L);
        given(eventRepository.findById(2L)).willReturn(Optional.of(event));
        assertThatThrownBy(() -> service.checkIn(
            2L,
            new AdmissionCheckInDtos.CheckInRequest("qr-token", null),
            actor(10L)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);

        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        assertThatThrownBy(() -> service.checkIn(
            2L,
            new AdmissionCheckInDtos.CheckInRequest(" ", null),
            actor(10L)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> service.checkIn(
            2L,
            new AdmissionCheckInDtos.CheckInRequest("qr-token", "A".repeat(101)),
            actor(10L)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(processor);
    }

    @Test
    void checkIn_convertsDuplicateAndInvalidResultAfterProcessorReturns() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        given(processor.checkIn(eq(1L), eq(event), eq("duplicate"), eq(null), eq(10L), any()))
            .willReturn(result(AdmissionCheckInProcessor.Outcome.DUPLICATE));
        given(processor.checkIn(eq(1L), eq(event), eq("invalid"), eq(null), eq(10L), any()))
            .willReturn(result(AdmissionCheckInProcessor.Outcome.INVALID));

        assertThatThrownBy(() -> service.checkIn(
            1L,
            new AdmissionCheckInDtos.CheckInRequest("duplicate", null),
            actor(10L)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_CHECK_IN_DUPLICATE);

        assertThatThrownBy(() -> service.checkIn(
            1L,
            new AdmissionCheckInDtos.CheckInRequest("invalid", null),
            actor(10L)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_CHECK_IN_INVALID_STATE);
    }

    @Test
    void cancelCheckIn_allowsCheckinStaffAndConvertsInvalidState() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            1L, 10L, EventRole.EVENT_MANAGER))
            .willReturn(false);
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            1L, 10L, EventRole.CHECKIN_STAFF))
            .willReturn(true);
        given(processor.cancelCheckIn(eq(1L), eq(event), eq(11L), eq(10L), any()))
            .willReturn(result(AdmissionCheckInProcessor.Outcome.SUCCESS));

        AdmissionCheckInDtos.CheckInCancellationResponse response =
            service.cancelCheckIn(1L, 11L, actor(10L));

        assertThat(response.status()).isEqualTo(AdmissionTicketStatus.USED);

        given(processor.cancelCheckIn(eq(1L), eq(event), eq(12L), eq(10L), any()))
            .willReturn(result(AdmissionCheckInProcessor.Outcome.CANCEL_INVALID));

        assertThatThrownBy(() -> service.cancelCheckIn(1L, 12L, actor(10L)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ADMISSION_CHECK_IN_CANCEL_INVALID_STATE);
    }

    @Test
    void getEventAdmissionLogs_allowsManagersButRejectsCheckinStaff() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            1L, 10L, EventRole.EVENT_MANAGER))
            .willReturn(true);
        given(admissionLogRepository.findEventAdmissionLogs(
            eq(1L), eq(AdmissionAction.CHECK_IN), eq(AdmissionResult.SUCCESS), any()))
            .willReturn(new PageImpl<>(List.of(logView())));

        Page<AdmissionCheckInDtos.LogListResponse> response = service.getEventAdmissionLogs(
            1L,
            AdmissionAction.CHECK_IN,
            AdmissionResult.SUCCESS,
            actor(10L),
            0,
            20
        );

        assertThat(response.getContent().getFirst().staffNickname()).isEqualTo("staff");

        Event otherEvent = event(2L);
        given(eventRepository.findById(2L)).willReturn(Optional.of(otherEvent));
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            2L, 20L, EventRole.EVENT_MANAGER))
            .willReturn(false);
        assertThatThrownBy(() -> service.getEventAdmissionLogs(
            2L,
            null,
            null,
            actor(20L),
            0,
            20
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    @Test
    void getEventAdmissionLogs_rejectsInvalidPagingBeforeRepositoryQuery() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);

        assertThatThrownBy(() -> service.getEventAdmissionLogs(1L, null, null, actor(10L), -1, 20))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verify(admissionLogRepository, never()).findEventAdmissionLogs(any(), any(), any(), any());
    }

    private AdmissionCheckInProcessor.ProcessResult result(
            AdmissionCheckInProcessor.Outcome outcome) {
        return new AdmissionCheckInProcessor.ProcessResult(
            outcome,
            11L,
            1L,
            "event",
            AdmissionTicketStatus.USED,
            OffsetDateTime.parse("2026-08-08T10:00:00+09:00"),
            21L,
            AdmissionAction.CHECK_IN,
            outcome == AdmissionCheckInProcessor.Outcome.SUCCESS
                ? AdmissionResult.SUCCESS
                : AdmissionResult.INVALID,
            OffsetDateTime.parse("2026-08-08T10:00:00+09:00")
        );
    }

    private AdmissionLogView logView() {
        return new AdmissionLogView() {
            @Override public Long getAdmissionLogId() { return 21L; }
            @Override public Long getAdmissionTicketId() { return 11L; }
            @Override public AdmissionAction getAction() { return AdmissionAction.CHECK_IN; }
            @Override public AdmissionResult getResult() { return AdmissionResult.SUCCESS; }
            @Override public String getGateName() { return "A Gate"; }
            @Override public String getStaffNickname() { return "staff"; }
            @Override public OffsetDateTime getProcessedAt() {
                return OffsetDateTime.parse("2026-08-08T10:00:00+09:00");
            }
        };
    }

    private AuthenticatedMemberDto actor(Long memberId) {
        return new AuthenticatedMemberDto(memberId, PlatformRole.USER);
    }

    private Event event() {
        return event(1L);
    }

    private Event event(Long id) {
        OffsetDateTime now = OffsetDateTime.now();
        return Event.builder()
            .id(id)
            .organizerOrganizationId(100L)
            .name("event")
            .eventType("CONFERENCE")
            .description("description")
            .venueName("venue")
            .address("address")
            .startAt(now.minusDays(1))
            .endAt(now.plusDays(1))
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
