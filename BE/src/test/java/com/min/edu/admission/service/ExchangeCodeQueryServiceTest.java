package com.min.edu.admission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.dto.ExchangeCodeDtos;
import com.min.edu.admission.dto.ExchangeCodeView;
import com.min.edu.admission.repository.ExchangeCodeRepository;
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
class ExchangeCodeQueryServiceTest {

    @Mock
    private ExchangeCodeRepository exchangeCodeRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventMemberRepository eventMemberRepository;

    @Mock
    private EventOrganizationMemberRepository organizationMemberRepository;

    private ExchangeCodeQueryService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeCodeQueryService(
            exchangeCodeRepository,
            eventRepository,
            eventMemberRepository,
            organizationMemberRepository
        );
    }

    @Test
    void getEventExchangeCodes_failsUnauthorizedBeforeEventLookup() {
        assertThatThrownBy(() -> service.getEventExchangeCodes(999L, null, null, 0, 20))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

        verifyNoInteractions(eventRepository, exchangeCodeRepository);
    }

    @Test
    void getEventExchangeCodes_failsWhenEventDoesNotExist() {
        given(eventRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventExchangeCodes(
            1L,
            null,
            actor(10L, PlatformRole.USER),
            0,
            20
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EVENT_NOT_FOUND);

        verifyNoInteractions(exchangeCodeRepository);
    }

    @Test
    void getEventExchangeCodes_allowsOrganizationOwnerOrManager() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        given(exchangeCodeRepository.findEventExchangeCodes(
            eq(1L), eq(null), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(view(
                7L,
                1L,
                "event",
                "A13FC9-12AA81-093FCD",
                3L,
                null,
                ExchangeCodeStatus.ISSUED,
                "holder",
                null,
                null,
                OffsetDateTime.parse("2026-08-06T10:00:00+09:00")
            ))));

        Page<ExchangeCodeDtos.EventListResponse> response =
            service.getEventExchangeCodes(1L, null, actor(10L, PlatformRole.USER), 0, 20);

        assertThat(response.getContent()).hasSize(1);
        ExchangeCodeDtos.EventListResponse item = response.getContent().getFirst();
        assertThat(item.maskedCode()).isEqualTo("A13F************3FCD");
        assertThat(item.source()).isEqualTo(ExchangeCodeDtos.Source.TICKET_ORDER);
        assertThat(item.holderNickname()).isEqualTo("holder");
        verify(eventMemberRepository, never())
            .existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(any(), any(), any());
    }

    @Test
    void getEventExchangeCodes_allowsActiveEventManager() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            1L, 10L, EventRole.EVENT_MANAGER))
            .willReturn(true);
        given(exchangeCodeRepository.findEventExchangeCodes(
            eq(1L), eq(ExchangeCodeStatus.REDEEMED), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(view(
                8L,
                1L,
                "event",
                "Z123",
                null,
                9L,
                ExchangeCodeStatus.REDEEMED,
                null,
                null,
                OffsetDateTime.parse("2026-08-06T10:00:00+09:00"),
                OffsetDateTime.parse("2026-08-06T09:00:00+09:00")
            ))));

        Page<ExchangeCodeDtos.EventListResponse> response = service.getEventExchangeCodes(
            1L,
            ExchangeCodeStatus.REDEEMED,
            actor(10L, PlatformRole.USER),
            0,
            20
        );

        assertThat(response.getContent().getFirst().source())
            .isEqualTo(ExchangeCodeDtos.Source.EXTERNAL_REQUEST);
        assertThat(response.getContent().getFirst().maskedCode()).isEqualTo("Z**3");
        assertThat(response.getContent().getFirst().holderNickname()).isNull();
    }

    @Test
    void getEventExchangeCodes_masksShortAbnormalCodesWithoutIndexError() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        given(exchangeCodeRepository.findEventExchangeCodes(
            eq(1L), eq(null), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(
                view(1L, 1L, "event", "", 1L, null,
                    ExchangeCodeStatus.ISSUED, null, null, null, OffsetDateTime.now()),
                view(2L, 1L, "event", "A", 1L, null,
                    ExchangeCodeStatus.ISSUED, null, null, null, OffsetDateTime.now()),
                view(3L, 1L, "event", "ABCDEFG", 1L, null,
                    ExchangeCodeStatus.ISSUED, null, null, null, OffsetDateTime.now()),
                view(4L, 1L, "event", "ABCDEFGH", 1L, null,
                    ExchangeCodeStatus.ISSUED, null, null, null, OffsetDateTime.now()),
                view(5L, 1L, "event", "ABCDEFGHI", 1L, null,
                    ExchangeCodeStatus.ISSUED, null, null, null, OffsetDateTime.now())
            )));

        Page<ExchangeCodeDtos.EventListResponse> response =
            service.getEventExchangeCodes(1L, null, actor(10L, PlatformRole.USER), 0, 20);

        assertThat(response.getContent())
            .extracting(ExchangeCodeDtos.EventListResponse::maskedCode)
            .containsExactly("", "*", "A*****G", "A******H", "ABCD*FGHI");
    }

    @Test
    void getEventExchangeCodes_failsForNormalUserCheckinStaffAndPlatformAdminWithoutEventAuthority() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));

        assertForbidden(actor(10L, PlatformRole.USER));
        assertForbidden(actor(99L, PlatformRole.PLATFORM_ADMIN));

        verify(exchangeCodeRepository, never()).findEventExchangeCodes(any(), any(), any());
    }

    @Test
    void getEventExchangeCodes_passesPageSizeStatusAndReturnsEmptyPage() {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);
        given(exchangeCodeRepository.findEventExchangeCodes(
            eq(1L), eq(ExchangeCodeStatus.CANCELLED), any(Pageable.class)))
            .willReturn(Page.empty());

        Page<ExchangeCodeDtos.EventListResponse> response = service.getEventExchangeCodes(
            1L,
            ExchangeCodeStatus.CANCELLED,
            actor(10L, PlatformRole.USER),
            2,
            5
        );

        assertThat(response.getContent()).isEmpty();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(exchangeCodeRepository).findEventExchangeCodes(
            eq(1L),
            eq(ExchangeCodeStatus.CANCELLED),
            pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void getEventExchangeCodes_rejectsInvalidPageAndSize() {
        assertInvalidEventPage(-1, 20);
        assertInvalidEventPage(0, 0);
        assertInvalidEventPage(0, -1);
        assertInvalidEventPage(0, 101);
    }

    @Test
    void getMyExchangeCodes_failsWhenUnauthenticated() {
        assertThatThrownBy(() -> service.getMyExchangeCodes(null, null, 0, 20))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

        verifyNoInteractions(exchangeCodeRepository);
    }

    @Test
    void getMyExchangeCodes_usesActorMemberIdAndReturnsRawCode() {
        given(exchangeCodeRepository.findMyExchangeCodes(
            eq(10L), eq(null), any(Pageable.class)))
            .willReturn(new PageImpl<>(List.of(view(
                7L,
                1L,
                "event",
                "A13FC9-12AA81-093FCD",
                3L,
                null,
                ExchangeCodeStatus.ISSUED,
                "holder",
                null,
                null,
                OffsetDateTime.parse("2026-08-06T10:00:00+09:00")
            ))));

        Page<ExchangeCodeDtos.MyListResponse> response =
            service.getMyExchangeCodes(null, actor(10L, PlatformRole.USER), 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().code())
            .isEqualTo("A13FC9-12AA81-093FCD");
        assertThat(response.getContent().getFirst().source())
            .isEqualTo(ExchangeCodeDtos.Source.TICKET_ORDER);
        verify(exchangeCodeRepository).findMyExchangeCodes(eq(10L), eq(null), any(Pageable.class));
    }

    @Test
    void getMyExchangeCodes_passesStatusAndPagingAndReturnsEmptyPage() {
        given(exchangeCodeRepository.findMyExchangeCodes(
            eq(10L), eq(ExchangeCodeStatus.EXPIRED), any(Pageable.class)))
            .willReturn(Page.empty());

        Page<ExchangeCodeDtos.MyListResponse> response = service.getMyExchangeCodes(
            ExchangeCodeStatus.EXPIRED,
            actor(10L, PlatformRole.USER),
            1,
            10
        );

        assertThat(response.getContent()).isEmpty();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(exchangeCodeRepository).findMyExchangeCodes(
            eq(10L),
            eq(ExchangeCodeStatus.EXPIRED),
            pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void getMyExchangeCodes_rejectsInvalidPageAndSize() {
        assertInvalidMyPage(-1, 20);
        assertInvalidMyPage(0, 0);
        assertInvalidMyPage(0, -1);
        assertInvalidMyPage(0, 101);
    }

    private void assertForbidden(AuthenticatedMemberDto actor) {
        assertThatThrownBy(() -> service.getEventExchangeCodes(1L, null, actor, 0, 20))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    private void assertInvalidEventPage(int page, int size) {
        Event event = event();
        given(eventRepository.findById(1L)).willReturn(Optional.of(event));
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            eq(100L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), any()))
            .willReturn(true);

        assertThatThrownBy(() -> service.getEventExchangeCodes(
            1L,
            null,
            actor(10L, PlatformRole.USER),
            page,
            size
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verify(exchangeCodeRepository, never()).findEventExchangeCodes(any(), any(), any());
    }

    private void assertInvalidMyPage(int page, int size) {
        assertThatThrownBy(() -> service.getMyExchangeCodes(
            null,
            actor(10L, PlatformRole.USER),
            page,
            size
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        verify(exchangeCodeRepository, never()).findMyExchangeCodes(any(), any(), any());
    }

    private AuthenticatedMemberDto actor(Long memberId, PlatformRole platformRole) {
        return new AuthenticatedMemberDto(memberId, platformRole);
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

    private ExchangeCodeView view(
            Long exchangeCodeId,
            Long eventId,
            String eventName,
            String code,
            Long ticketOrderId,
            Long exchangeCodeRequestId,
            ExchangeCodeStatus status,
            String holderNickname,
            OffsetDateTime expiresAt,
            OffsetDateTime redeemedAt,
            OffsetDateTime createdAt) {
        return new ExchangeCodeView() {
            @Override public Long getExchangeCodeId() { return exchangeCodeId; }
            @Override public Long getEventId() { return eventId; }
            @Override public String getEventName() { return eventName; }
            @Override public String getCode() { return code; }
            @Override public Long getTicketOrderId() { return ticketOrderId; }
            @Override public Long getExchangeCodeRequestId() { return exchangeCodeRequestId; }
            @Override public ExchangeCodeStatus getStatus() { return status; }
            @Override public String getHolderNickname() { return holderNickname; }
            @Override public OffsetDateTime getExpiresAt() { return expiresAt; }
            @Override public OffsetDateTime getRedeemedAt() { return redeemedAt; }
            @Override public OffsetDateTime getCreatedAt() { return createdAt; }
        };
    }
}
