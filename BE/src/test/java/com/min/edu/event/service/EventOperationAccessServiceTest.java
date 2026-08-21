package com.min.edu.event.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EventOperationAccessServiceTest {

    private final EventRepository eventRepository = Mockito.mock(EventRepository.class);
    private final EventMemberRepository eventMemberRepository = Mockito.mock(EventMemberRepository.class);
    private final EventOrganizationMemberRepository organizationMemberRepository =
        Mockito.mock(EventOrganizationMemberRepository.class);
    private final EventOperationAccessService service = new EventOperationAccessService(
        eventRepository,
        eventMemberRepository,
        organizationMemberRepository
    );

    @Test
    void allowsOrganizationOwner() {
        givenEvent();
        givenOrganizationRole(10L, OrganizationRole.OWNER, true);

        service.requireOperationalAccess(100L, actor(10L));
    }

    @Test
    void allowsOrganizationManager() {
        givenEvent();
        givenOrganizationRole(10L, OrganizationRole.MANAGER, true);

        service.requireOperationalAccess(100L, actor(10L));
    }

    @Test
    void allowsEventManager() {
        givenEvent();
        givenEventRole(10L, EventRole.EVENT_MANAGER, true);

        service.requireOperationalAccess(100L, actor(10L));
    }

    @Test
    void allowsCheckinStaff() {
        givenEvent();
        givenEventRole(10L, EventRole.CHECKIN_STAFF, true);

        service.requireOperationalAccess(100L, actor(10L));
    }

    @Test
    void rejectsGeneralUserWithoutOperationalRelationship() {
        givenEvent();

        assertForbidden(10L);
    }

    @Test
    void rejectsUnrelatedUser() {
        givenEvent();
        givenOrganizationRole(20L, OrganizationRole.OWNER, true);
        givenEventRole(20L, EventRole.EVENT_MANAGER, true);

        assertForbidden(10L);
    }

    private void assertForbidden(Long memberId) {
        assertThatThrownBy(() -> service.requireOperationalAccess(100L, actor(memberId)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.FORBIDDEN);
    }

    private void givenEvent() {
        given(eventRepository.findById(100L)).willReturn(Optional.of(event()));
    }

    @SuppressWarnings("unchecked")
    private void givenOrganizationRole(Long memberId, OrganizationRole role, boolean exists) {
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
            Mockito.eq(500L),
            Mockito.eq(memberId),
            Mockito.eq(OrganizationMemberStatus.ACTIVE),
            Mockito.argThat(roles -> ((Collection<OrganizationRole>) roles).contains(role))
        )).willReturn(exists);
    }

    private void givenEventRole(Long memberId, EventRole role, boolean exists) {
        given(eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
            100L,
            memberId,
            role
        )).willReturn(exists);
    }

    private AuthenticatedMemberDto actor(Long memberId) {
        return new AuthenticatedMemberDto(memberId, PlatformRole.USER);
    }

    private Event event() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-18T10:00:00+09:00");
        return Event.builder()
            .id(100L)
            .organizerOrganizationId(500L)
            .name("Eventoday Conference")
            .eventType("CONFERENCE")
            .description("description")
            .venueName("Main Hall")
            .address("Seoul")
            .startAt(now.minusDays(1))
            .endAt(now.plusDays(1))
            .ticketPrice(java.math.BigDecimal.TEN)
            .ticketTotalQuantity(100)
            .ticketSoldQuantity(0)
            .ticketPurchaseLimit(2)
            .status(com.min.edu.event.domain.EventStatus.PUBLISHED)
            .noShowGraceMinutes(10)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
