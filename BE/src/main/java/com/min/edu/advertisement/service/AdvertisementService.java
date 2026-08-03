package com.min.edu.advertisement.service;

import com.min.edu.advertisement.domain.Advertisement;
import com.min.edu.advertisement.domain.AdvertisementStatus;
import com.min.edu.advertisement.dto.AdvertisementDtos;
import com.min.edu.advertisement.repository.AdvertisementBoothRepository;
import com.min.edu.advertisement.repository.AdvertisementRepository;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
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
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdvertisementService {
    private static final List<OrganizationRole> MANAGER_ROLES =
            List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER);
    private final AdvertisementRepository advertisementRepository;
    private final AdvertisementBoothRepository boothRepository;
    private final EventRepository eventRepository;
    private final EventOrganizationMemberRepository organizationMemberRepository;
    private final EventMemberRepository eventMemberRepository;

    public AdvertisementService(AdvertisementRepository advertisementRepository,
            AdvertisementBoothRepository boothRepository, EventRepository eventRepository,
            EventOrganizationMemberRepository organizationMemberRepository,
            EventMemberRepository eventMemberRepository) {
        this.advertisementRepository = advertisementRepository;
        this.boothRepository = boothRepository;
        this.eventRepository = eventRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.eventMemberRepository = eventMemberRepository;
    }

    public List<AdvertisementDtos.Response> findActive(Long eventId) {
        OffsetDateTime now = OffsetDateTime.now();
        return advertisementRepository
                .findAllByStatusInAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                        List.of(AdvertisementStatus.ACTIVE, AdvertisementStatus.SCHEDULED), now, now)
                .stream().filter(ad -> eventId == null || eventId.equals(ad.getEventId()))
                .sorted(activeComparator()).map(AdvertisementDtos.Response::from).toList();
    }

    @Transactional
    public AdvertisementDtos.Response createEventAd(Long eventId, AdvertisementDtos.SaveRequest request,
            AuthenticatedMemberDto actor) {
        Event event = getEvent(eventId);
        if (!event.getOrganizerOrganizationId().equals(request.applicantOrganizationId()))
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        requireOrganizationManager(request.applicantOrganizationId(), actor);
        validatePeriod(request.startAt(), request.endAt());
        return save(eventId, null, request, AdvertisementStatus.PAYMENT_PENDING);
    }

    @Transactional
    public AdvertisementDtos.Response createBoothAd(Long boothId, AdvertisementDtos.SaveRequest request,
            AuthenticatedMemberDto actor) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        if (!request.applicantOrganizationId().equals(booth.getAssignedOrganizationId()))
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        requireOrganizationManager(request.applicantOrganizationId(), actor);
        validatePeriod(request.startAt(), request.endAt());
        return save(null, boothId, request, AdvertisementStatus.REVIEW_PENDING);
    }

    public Page<AdvertisementDtos.Response> findOrganizationAds(Long organizationId,
            AuthenticatedMemberDto actor, Pageable pageable) {
        requireOrganizationMember(organizationId, actor);
        Specification<Advertisement> spec = (root, query, cb) ->
                cb.equal(root.get("applicantOrganizationId"), organizationId);
        return advertisementRepository.findAll(spec, pageable).map(AdvertisementDtos.Response::from);
    }

    public AdvertisementDtos.Response get(Long id, AuthenticatedMemberDto actor) {
        Advertisement ad = getAd(id); requireOwnerOrAdmin(ad, actor);
        return AdvertisementDtos.Response.from(ad);
    }

    @Transactional
    public AdvertisementDtos.Response update(Long id, AdvertisementDtos.UpdateRequest request,
            AuthenticatedMemberDto actor) {
        Advertisement ad = getAd(id); requireOrganizationManager(ad.getApplicantOrganizationId(), actor);
        validatePeriod(request.startAt(), request.endAt());
        transition(() -> ad.update(request.bannerFileId(), request.adText(), request.startAt(),
                request.endAt(), OffsetDateTime.now()));
        return AdvertisementDtos.Response.from(ad);
    }

    @Transactional
    public void cancel(Long id, AuthenticatedMemberDto actor) {
        Advertisement ad = getAd(id); requireOrganizationManager(ad.getApplicantOrganizationId(), actor);
        transition(() -> ad.cancel(OffsetDateTime.now()));
    }

    public Page<AdvertisementDtos.Response> findAdminAds(AdvertisementStatus status,
            AuthenticatedMemberDto actor, Pageable pageable) {
        requireAdmin(actor);
        Specification<Advertisement> spec = status == null ? null :
                (root, query, cb) -> cb.equal(root.get("status"), status);
        return advertisementRepository.findAll(spec, pageable).map(AdvertisementDtos.Response::from);
    }

    @Transactional
    public void approve(Long id, AuthenticatedMemberDto actor) {
        requireAdmin(actor); Advertisement ad = getAd(id);
        transition(() -> ad.approve(actor.getMemberId(), OffsetDateTime.now()));
    }

    @Transactional
    public void reject(Long id, String reason, AuthenticatedMemberDto actor) {
        requireAdmin(actor); Advertisement ad = getAd(id);
        transition(() -> ad.reject(actor.getMemberId(), reason, OffsetDateTime.now()));
    }

    public List<AdvertisementDtos.BoothCandidate> findBoothCandidates(Long eventId,
            AuthenticatedMemberDto actor) {
        Event event = getEvent(eventId); requireEventManager(event, actor);
        return boothRepository.findAllByEventIdAndAssignedOrganizationIdIsNotNull(eventId).stream()
                .map(booth -> new AdvertisementDtos.BoothCandidate(booth.getId(), booth.getBoothCode(),
                        booth.getDisplayName(), booth.getAssignedOrganizationId())).toList();
    }

    private AdvertisementDtos.Response save(Long eventId, Long boothId,
            AdvertisementDtos.SaveRequest request, AdvertisementStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        Advertisement ad = Advertisement.builder().eventId(eventId).boothId(boothId)
                .applicantOrganizationId(request.applicantOrganizationId())
                .bannerFileId(request.bannerFileId()).adText(request.adText())
                .startAt(request.startAt()).endAt(request.endAt()).status(status)
                .createdAt(now).updatedAt(now).build();
        return AdvertisementDtos.Response.from(advertisementRepository.save(ad));
    }

    private Comparator<Advertisement> activeComparator() {
        return Comparator.comparing((Advertisement ad) -> {
            if (ad.getEventId() == null) return OffsetDateTime.MAX;
            return eventRepository.findById(ad.getEventId()).map(Event::getStartAt).orElse(OffsetDateTime.MAX);
        }).thenComparing(Advertisement::getId);
    }
    private Event getEvent(Long id) { return eventRepository.findById(id)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND)); }
    private Advertisement getAd(Long id) { return advertisementRepository.findById(id)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND)); }
    private void requireOwnerOrAdmin(Advertisement ad, AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        if (actor.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) return;
        requireOrganizationMember(ad.getApplicantOrganizationId(), actor);
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
        boolean organizer = organizationMemberRepository
                .existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                        event.getOrganizerOrganizationId(), actor.getMemberId(), OrganizationMemberStatus.ACTIVE, MANAGER_ROLES);
        boolean assigned = eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                event.getId(), actor.getMemberId(), EventRole.EVENT_MANAGER);
        if (!organizer && !assigned) throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }
    private void requireAdmin(AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        if (actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN)
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }
    private void requireAuthenticated(AuthenticatedMemberDto actor) {
        if (actor == null) throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
    }
    private void validatePeriod(OffsetDateTime start, OffsetDateTime end) {
        if (!start.isBefore(end)) throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
    }
    private void transition(Runnable action) {
        try { action.run(); } catch (IllegalStateException e) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
