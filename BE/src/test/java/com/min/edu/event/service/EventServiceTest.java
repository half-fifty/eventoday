package com.min.edu.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.admin.service.PlatformAuditService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.dto.EventDtos;
import com.min.edu.event.policy.EventOperationDeadlinePolicy;
import com.min.edu.event.repository.AdmissionEventProjection;
import com.min.edu.event.repository.EventBoothRecruitmentRepository;
import com.min.edu.event.repository.EventExhibitCategoryRepository;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventOrganizationRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.event.repository.ExhibitCategoryRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private EventMemberRepository eventMemberRepository;
    @Mock private EventOrganizationMemberRepository organizationMemberRepository;
    @Mock private EventOrganizationRepository organizationRepository;
    @Mock private EventBoothRecruitmentRepository boothRecruitmentRepository;
    @Mock private ExhibitCategoryRepository exhibitCategoryRepository;
    @Mock private EventExhibitCategoryRepository eventExhibitCategoryRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private PlatformAuditService platformAuditService;

    @Test
    void findMyAdmissionEvents_returnsActiveAdmissionRolesForCurrentMember() {
        EventService service = service();
        OffsetDateTime startAt = OffsetDateTime.now().plusDays(1);
        OffsetDateTime endAt = startAt.plusDays(2);
        given(eventMemberRepository.findAdmissionEventsByMemberId(10L,
                List.of(EventRole.EVENT_MANAGER, EventRole.CHECKIN_STAFF)))
                .willReturn(List.of(
                        projection(1L, "행사 관리자 배정 행사", startAt, endAt, EventRole.EVENT_MANAGER),
                        projection(2L, "입장 스태프 배정 행사", startAt, endAt, EventRole.CHECKIN_STAFF)
                ));

        List<EventDtos.AdmissionEventResponse> responses =
                service.findMyAdmissionEvents(actor(10L));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).eventId()).isEqualTo(1L);
        assertThat(responses.get(0).role()).isEqualTo(EventRole.EVENT_MANAGER);
        assertThat(responses.get(1).eventId()).isEqualTo(2L);
        assertThat(responses.get(1).role()).isEqualTo(EventRole.CHECKIN_STAFF);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findMyAdmissionEvents_queriesOnlyCurrentMemberAndAdmissionRoles() {
        EventService service = service();
        given(eventMemberRepository.findAdmissionEventsByMemberId(
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.anyCollection()))
                .willReturn(List.of());

        service.findMyAdmissionEvents(actor(20L));

        ArgumentCaptor<Collection<EventRole>> rolesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(eventMemberRepository).findAdmissionEventsByMemberId(
                org.mockito.ArgumentMatchers.eq(20L), rolesCaptor.capture());
        assertThat(rolesCaptor.getValue())
                .containsExactlyInAnyOrder(EventRole.EVENT_MANAGER, EventRole.CHECKIN_STAFF);
    }

    @Test
    void findMyAdmissionEvents_returnsEmptyWhenNoActiveAdmissionAssignmentExists() {
        EventService service = service();
        given(eventMemberRepository.findAdmissionEventsByMemberId(30L,
                List.of(EventRole.EVENT_MANAGER, EventRole.CHECKIN_STAFF)))
                .willReturn(List.of());

        assertThat(service.findMyAdmissionEvents(actor(30L))).isEmpty();
    }

    @Test
    void create_rejectsWhenSalesStartEqualsEffectiveEndWithNullSalesEnd() {
        EventService service = service();
        OffsetDateTime eventEndAt = OffsetDateTime.now().plusDays(1);
        EventDtos.SaveRequest request = saveRequest(
                eventEndAt.minusHours(1),
                null,
                eventEndAt);
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(OrganizationMemberStatus.ACTIVE),
                org.mockito.ArgumentMatchers.anyCollection()))
                .willReturn(true);

        assertThatThrownBy(() -> service.create(100L, request, actor(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void create_rejectsWhenSalesStartAfterEffectiveEndWithNullSalesEnd() {
        EventService service = service();
        OffsetDateTime eventEndAt = OffsetDateTime.now().plusDays(1);
        EventDtos.SaveRequest request = saveRequest(
                eventEndAt.minusMinutes(30),
                null,
                eventEndAt);
        given(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(OrganizationMemberStatus.ACTIVE),
                org.mockito.ArgumentMatchers.anyCollection()))
                .willReturn(true);

        assertThatThrownBy(() -> service.create(100L, request, actor(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
    }

    private EventService service() {
        return new EventService(eventRepository, eventMemberRepository, organizationMemberRepository,
                organizationRepository, boothRecruitmentRepository, exhibitCategoryRepository,
                eventExhibitCategoryRepository, applicationEventPublisher, platformAuditService,
                new EventOperationDeadlinePolicy());
    }

    private AuthenticatedMemberDto actor(Long memberId) {
        return new AuthenticatedMemberDto(memberId, PlatformRole.USER);
    }

    private AdmissionEventProjection projection(Long eventId, String eventName,
            OffsetDateTime startAt, OffsetDateTime endAt, EventRole role) {
        return new AdmissionEventProjection() {
            @Override
            public Long getEventId() {
                return eventId;
            }

            @Override
            public String getEventName() {
                return eventName;
            }

            @Override
            public OffsetDateTime getStartAt() {
                return startAt;
            }

            @Override
            public OffsetDateTime getEndAt() {
                return endAt;
            }

            @Override
            public EventRole getRole() {
                return role;
            }
        };
    }

    private EventDtos.SaveRequest saveRequest(
            OffsetDateTime ticketSalesStartAt,
            OffsetDateTime ticketSalesEndAt,
            OffsetDateTime endAt) {
        return new EventDtos.SaveRequest(
                "event",
                "EXPO",
                "short",
                "description",
                "venue",
                "서울시 강남구 테헤란로 1",
                "contact@example.com",
                "010-1234-5678",
                "12345",
                "detail",
                null,
                null,
                null,
                Set.of("IT"),
                endAt.minusDays(1),
                endAt,
                ticketSalesStartAt,
                ticketSalesEndAt,
                BigDecimal.ZERO,
                100,
                5,
                1L,
                false,
                false,
                false,
                10);
    }
}
