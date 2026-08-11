package com.min.edu.event.service;

import com.min.edu.admin.service.PlatformAuditService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventMember;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.domain.EventExhibitCategory;
import com.min.edu.event.domain.EventExhibitCategoryId;
import com.min.edu.event.domain.ExhibitCategory;
import com.min.edu.event.domain.RegionCode;
import com.min.edu.event.dto.EventDtos;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventBoothRecruitmentRepository;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.event.repository.EventOrganizationRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.event.repository.EventExhibitCategoryRepository;
import com.min.edu.event.repository.ExhibitCategoryRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventService {
    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventOrganizationMemberRepository organizationMemberRepository;
    private final EventOrganizationRepository organizationRepository;
    private final EventBoothRecruitmentRepository boothRecruitmentRepository;
    private final ExhibitCategoryRepository exhibitCategoryRepository;
    private final EventExhibitCategoryRepository eventExhibitCategoryRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlatformAuditService platformAuditService;

    public EventService(EventRepository eventRepository, EventMemberRepository eventMemberRepository,
            EventOrganizationMemberRepository organizationMemberRepository,
            EventOrganizationRepository organizationRepository,
            EventBoothRecruitmentRepository boothRecruitmentRepository,
            ExhibitCategoryRepository exhibitCategoryRepository,
            EventExhibitCategoryRepository eventExhibitCategoryRepository,
            ApplicationEventPublisher applicationEventPublisher,
            PlatformAuditService platformAuditService) {
        this.eventRepository = eventRepository;
        this.eventMemberRepository = eventMemberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.organizationRepository = organizationRepository;
        this.boothRecruitmentRepository = boothRecruitmentRepository;
        this.exhibitCategoryRepository = exhibitCategoryRepository;
        this.eventExhibitCategoryRepository = eventExhibitCategoryRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.platformAuditService = platformAuditService;
    }

    public List<EventDtos.ManagedOrganization> findManagedOrganizations(AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        List<Long> organizationIds = organizationMemberRepository
                .findAllByMemberIdAndStatusAndOrganizationRoleIn(
                        actor.getMemberId(), OrganizationMemberStatus.ACTIVE, MANAGER_ROLES)
                .stream().map(member -> member.getOrganizationId()).toList();
        if (organizationIds.isEmpty()) return List.of();
        return organizationRepository.findAllByIdInOrderByNameAsc(organizationIds).stream()
                .map(EventDtos.ManagedOrganization::from).toList();
    }

    public Page<EventDtos.Summary> findPublicEvents(String keyword, String eventType, RegionCode regionCode,
            List<String> exhibitCategoryCodes, String venueName,
            OffsetDateTime startFrom, OffsetDateTime startTo, Pageable pageable) {
        Specification<Event> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), EventStatus.PUBLISHED));
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%"));
            }
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (regionCode != null) predicates.add(cb.equal(root.get("regionCode"), regionCode));
            if (venueName != null && !venueName.isBlank()) {
                predicates.add(cb.equal(root.get("venueName"), venueName.trim()));
            }
            if (exhibitCategoryCodes != null && !exhibitCategoryCodes.isEmpty()) {
                Subquery<Long> categoryQuery = query.subquery(Long.class);
                Root<EventExhibitCategory> relation = categoryQuery.from(EventExhibitCategory.class);
                Root<ExhibitCategory> category = categoryQuery.from(ExhibitCategory.class);
                categoryQuery.select(relation.get("id").get("eventId"))
                        .where(cb.equal(relation.get("id").get("eventId"), root.get("id")),
                                cb.equal(relation.get("id").get("categoryId"), category.get("id")),
                                category.get("code").in(exhibitCategoryCodes),
                                cb.isTrue(category.get("active")));
                predicates.add(cb.exists(categoryQuery));
            }
            if (startFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("startAt"), startFrom));
            if (startTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("startAt"), startTo));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return summaries(eventRepository.findAll(spec, pageable));
    }

    public List<EventDtos.ExhibitCategoryResponse> findExhibitCategories() {
        return exhibitCategoryRepository.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(category -> new EventDtos.ExhibitCategoryResponse(category.getCode(), category.getName())).toList();
    }

    public EventDtos.PublicDetail getPublicEvent(Long eventId) {
        Event event = getEvent(eventId);
        if (event.getStatus() != EventStatus.PUBLISHED) throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        return EventDtos.PublicDetail.from(event, categoryCodes(event.getId()));
    }

    public Page<EventDtos.Summary> findOrganizationEvents(Long organizationId,
            AuthenticatedMemberDto actor, Pageable pageable) {
        requireOrganizationMember(organizationId, actor);
        Specification<Event> spec = (root, query, cb) ->
                cb.equal(root.get("organizerOrganizationId"), organizationId);
        return summaries(eventRepository.findAll(spec, pageable));
    }

    public EventDtos.Detail getManagedEvent(Long organizationId, Long eventId,
            AuthenticatedMemberDto actor) {
        requireOrganizationMember(organizationId, actor);
        Event event = getEvent(eventId);
        requireEventOrganization(event, organizationId);
        return EventDtos.Detail.from(event, categoryCodes(event.getId()));
    }

    @Transactional
    public EventDtos.Detail create(Long organizationId, EventDtos.SaveRequest request,
            AuthenticatedMemberDto actor) {
        requireOrganizationManager(organizationId, actor);
        validateRequest(request);
        OffsetDateTime now = OffsetDateTime.now();
        Event event = Event.builder()
                .organizerOrganizationId(organizationId).name(request.name())
                .eventType(request.eventType()).shortDescription(request.shortDescription())
                .description(request.description()).venueName(request.venueName()).address(request.address())
                .contactEmail(request.contactEmail()).contactPhone(request.contactPhone())
                .postalCode(request.postalCode()).addressDetail(request.addressDetail())
                .latitude(request.latitude()).longitude(request.longitude()).kakaoPlaceId(request.kakaoPlaceId())
                .regionCode(RegionCode.fromAddress(request.address()))
                .startAt(request.startAt()).endAt(request.endAt())
                .ticketSalesStartAt(request.ticketSalesStartAt()).ticketSalesEndAt(request.ticketSalesEndAt())
                .ticketPrice(request.ticketPrice()).ticketTotalQuantity(request.ticketTotalQuantity())
                .ticketSoldQuantity(0).ticketPurchaseLimit(request.ticketPurchaseLimit())
                .representativeFileId(request.representativeFileId()).status(EventStatus.PREPARING)
                .boothRecruitmentEnabled(request.boothRecruitmentEnabled())
                .venueMapEnabled(request.venueMapEnabled()).boothReservationEnabled(request.boothReservationEnabled())
                .noShowGraceMinutes(request.noShowGraceMinutes()).createdAt(now).updatedAt(now).build();
        Event saved = eventRepository.save(event);
        eventMemberRepository.save(EventMember.builder()
                .eventId(saved.getId()).memberId(actor.getMemberId())
                .eventRole(EventRole.EVENT_MANAGER).active(true)
                .createdAt(now).build());
        replaceCategories(saved.getId(), request.exhibitCategoryCodes());
        return EventDtos.Detail.from(saved, categoryCodes(saved.getId()));
    }

    @Transactional
    public EventDtos.Detail update(Long organizationId, Long eventId, EventDtos.SaveRequest request,
            AuthenticatedMemberDto actor) {
        requireOrganizationManager(organizationId, actor);
        validateRequest(request);
        Event event = getEvent(eventId);
        requireEventOrganization(event, organizationId);
        event.update(request.name(), request.eventType(), request.shortDescription(), request.description(),
                request.venueName(), request.address(), request.postalCode(), request.addressDetail(),
                request.contactEmail(), request.contactPhone(),
                request.latitude(), request.longitude(), request.kakaoPlaceId(), RegionCode.fromAddress(request.address()),
                request.startAt(), request.endAt(),
                request.ticketSalesStartAt(), request.ticketSalesEndAt(), request.ticketPrice(),
                request.ticketTotalQuantity(), request.ticketPurchaseLimit(), request.representativeFileId(),
                request.boothRecruitmentEnabled(), request.venueMapEnabled(), request.boothReservationEnabled(),
                request.noShowGraceMinutes(), OffsetDateTime.now());
        replaceCategories(eventId, request.exhibitCategoryCodes());
        return EventDtos.Detail.from(event, categoryCodes(eventId));
    }

    @Transactional public void submit(Long eventId, AuthenticatedMemberDto actor) {
        Event event = getEvent(eventId); requireEventManager(event, actor);
        if (event.isBoothRecruitmentEnabled()
                && !boothRecruitmentRepository.existsByEventIdAndStatus(
                        eventId, BoothRecruitmentStatus.COMPLETED)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        transition(() -> event.submit(OffsetDateTime.now()));
        platformAuditService.record(actor.getMemberId(), "EVENT", "SUBMITTED",
                event.getId(), event.getName(), null);
    }
    @Transactional public void publish(Long eventId, AuthenticatedMemberDto actor) {
        Event event = getEvent(eventId); requireEventManager(event, actor);
        transition(() -> event.publish(OffsetDateTime.now()));
        platformAuditService.record(actor.getMemberId(), "EVENT", "PUBLISHED",
                event.getId(), event.getName(), null);
    }
    @Transactional public void cancel(Long eventId, AuthenticatedMemberDto actor) {
        Event event = getEvent(eventId); requireEventManager(event, actor);
        transition(() -> event.cancel(OffsetDateTime.now()));
        platformAuditService.record(actor.getMemberId(), "EVENT", "CANCELLED",
                event.getId(), event.getName(), null);
    }
    @Transactional public void approve(Long eventId, AuthenticatedMemberDto actor) {
        requireAdmin(actor); Event event = getEvent(eventId);
        transition(() -> event.approve(OffsetDateTime.now()));
        platformAuditService.record(actor.getMemberId(), "EVENT", "APPROVED",
                event.getId(), event.getName(), null);
        applicationEventPublisher.publishEvent(new EventReviewDecision(event.getId(), event.getOrganizerOrganizationId(), event.getName(), true, null));
    }
    @Transactional public void reject(Long eventId, String reason, AuthenticatedMemberDto actor) {
        requireAdmin(actor); Event event = getEvent(eventId);
        transition(() -> event.reject(reason, OffsetDateTime.now()));
        platformAuditService.record(actor.getMemberId(), "EVENT", "REJECTED",
                event.getId(), event.getName(), reason);
        applicationEventPublisher.publishEvent(new EventReviewDecision(event.getId(), event.getOrganizerOrganizationId(), event.getName(), false, reason));
    }
    @Transactional public void suspend(Long eventId, AuthenticatedMemberDto actor) {
        requireAdmin(actor); Event event = getEvent(eventId);
        transition(() -> event.suspend(OffsetDateTime.now()));
        platformAuditService.record(actor.getMemberId(), "EVENT", "SUSPENDED",
                event.getId(), event.getName(), null);
    }

    public Page<EventDtos.Summary> findAdminEvents(EventStatus status, AuthenticatedMemberDto actor,
            Pageable pageable) {
        requireAdmin(actor);
        Specification<Event> spec = status == null ? null :
                (root, query, cb) -> cb.equal(root.get("status"), status);
        return summaries(eventRepository.findAll(spec, pageable));
    }

    public EventDtos.Detail getAdminEvent(Long eventId, AuthenticatedMemberDto actor) {
        requireAdmin(actor); Event event = getEvent(eventId); return EventDtos.Detail.from(event, categoryCodes(eventId));
    }

    public List<EventDtos.MemberResponse> getMembers(Long eventId, AuthenticatedMemberDto actor) {
        Event event = getEvent(eventId); requireEventManager(event, actor);
        return eventMemberRepository.findAllByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(EventDtos.MemberResponse::from).toList();
    }

    @Transactional
    public EventDtos.MemberResponse addMember(Long eventId, EventDtos.MemberRequest request,
            AuthenticatedMemberDto actor) {
        Event event = getEvent(eventId); requireEventManager(event, actor);
        EventMember member = eventMemberRepository.findByEventIdAndMemberId(eventId, request.memberId())
                .orElseGet(() -> EventMember.builder().eventId(eventId).memberId(request.memberId())
                        .createdAt(OffsetDateTime.now()).build());
        member.update(request.eventRole(), true);
        return EventDtos.MemberResponse.from(eventMemberRepository.save(member));
    }

    @Transactional
    public EventDtos.MemberResponse updateMember(Long eventId, Long memberId,
            EventDtos.MemberUpdateRequest request, AuthenticatedMemberDto actor) {
        Event event = getEvent(eventId); requireEventManager(event, actor);
        EventMember member = getMember(eventId, memberId);
        member.update(request.eventRole(), request.active());
        return EventDtos.MemberResponse.from(member);
    }

    @Transactional
    public void removeMember(Long eventId, Long memberId, AuthenticatedMemberDto actor) {
        Event event = getEvent(eventId); requireEventManager(event, actor);
        eventMemberRepository.delete(getMember(eventId, memberId));
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
    }
    private EventMember getMember(Long eventId, Long memberId) {
        return eventMemberRepository.findByEventIdAndMemberId(eventId, memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
    }
    private void requireEventOrganization(Event event, Long organizationId) {
        if (!event.getOrganizerOrganizationId().equals(organizationId)) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }
    private void requireOrganizationMember(Long organizationId, AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        if (!organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatus(
                organizationId, actor.getMemberId(), OrganizationMemberStatus.ACTIVE))
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }
    private void requireOrganizationManager(Long organizationId, AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        if (!organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                organizationId, actor.getMemberId(), OrganizationMemberStatus.ACTIVE, MANAGER_ROLES))
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }
    private void requireEventManager(Event event, AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        boolean organizerManager = organizationMemberRepository
                .existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                        event.getOrganizerOrganizationId(), actor.getMemberId(),
                        OrganizationMemberStatus.ACTIVE, MANAGER_ROLES);
        boolean assigned = eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                event.getId(), actor.getMemberId(), EventRole.EVENT_MANAGER);
        if (!organizerManager && !assigned) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }
    private void requireAdmin(AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        if (actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }
    private void requireAuthenticated(AuthenticatedMemberDto actor) {
        if (actor == null) throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
    }
    private void validateRequest(EventDtos.SaveRequest request) {
        if (!request.startAt().isBefore(request.endAt())) throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        if (request.ticketSalesStartAt() != null && request.ticketSalesEndAt() != null
                && !request.ticketSalesStartAt().isBefore(request.ticketSalesEndAt()))
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        if (request.ticketTotalQuantity() < request.ticketPurchaseLimit())
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
    }

    private Page<EventDtos.Summary> summaries(Page<Event> events) {
        Map<Long, List<String>> codesByEventId = categoryCodesByEventIds(
                events.getContent().stream().map(Event::getId).toList());
        return events.map(event -> EventDtos.Summary.from(
                event, codesByEventId.getOrDefault(event.getId(), List.of())));
    }

    private Map<Long, List<String>> categoryCodesByEventIds(List<Long> eventIds) {
        if (eventIds.isEmpty()) return Map.of();
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (EventExhibitCategoryRepository.EventCategoryCodeRow row
                : eventExhibitCategoryRepository.findCodesByEventIds(eventIds)) {
            result.computeIfAbsent(row.getEventId(), ignored -> new ArrayList<>()).add(row.getCode());
        }
        return result;
    }

    private List<String> categoryCodes(Long eventId) {
        return eventExhibitCategoryRepository.findCodesByEventId(eventId);
    }

    private void replaceCategories(Long eventId, Set<String> requestedCodes) {
        List<ExhibitCategory> categories = exhibitCategoryRepository.findAllByCodeInAndActiveTrue(requestedCodes);
        if (categories.size() != requestedCodes.size()) throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        eventExhibitCategoryRepository.deleteAllByIdEventId(eventId);
        eventExhibitCategoryRepository.flush();
        eventExhibitCategoryRepository.saveAll(categories.stream()
                .map(category -> new EventExhibitCategory(new EventExhibitCategoryId(eventId, category.getId())))
                .toList());
    }

    private void transition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
