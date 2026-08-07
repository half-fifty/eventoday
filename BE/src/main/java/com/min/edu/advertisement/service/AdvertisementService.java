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
import com.min.edu.file.service.FileService;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.member.domain.Member;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.time.Duration;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.support.OrderNoGenerator;
import org.springframework.beans.factory.annotation.Value;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final PaymentOrderRepository paymentOrderRepository;
    private final OrderNoGenerator orderNoGenerator;
    private final MemberRepository memberRepository;
    private final BigDecimal eventAdPrice;
    private final Duration paymentExpiry;
    private final FileService fileService;

    public AdvertisementService(AdvertisementRepository advertisementRepository,
            AdvertisementBoothRepository boothRepository, EventRepository eventRepository,
            EventOrganizationMemberRepository organizationMemberRepository,
            EventMemberRepository eventMemberRepository, PaymentOrderRepository paymentOrderRepository,
            OrderNoGenerator orderNoGenerator, MemberRepository memberRepository,
            FileService fileService,
            @Value("${advertisement.event-ad-price:100000}") BigDecimal eventAdPrice,
            @Value("${advertisement.payment-expiry:PT30M}") Duration paymentExpiry) {
        this.advertisementRepository = advertisementRepository;
        this.boothRepository = boothRepository;
        this.eventRepository = eventRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.eventMemberRepository = eventMemberRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.orderNoGenerator = orderNoGenerator;
        this.memberRepository = memberRepository;
        this.fileService = fileService;
        this.eventAdPrice = eventAdPrice;
        this.paymentExpiry = paymentExpiry;
    }

    public List<AdvertisementDtos.Response> findActive(Long eventId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<AdvertisementStatus> statuses =
                List.of(AdvertisementStatus.ACTIVE, AdvertisementStatus.SCHEDULED);
        List<Advertisement> advertisements = eventId == null
                ? advertisementRepository
                        .findAllByStatusInAndStartAtLessThanEqualAndEndAtGreaterThan(
                                statuses, now, now)
                : advertisementRepository
                        .findAllByEventIdAndStatusInAndStartAtLessThanEqualAndEndAtGreaterThan(
                                eventId, statuses, now, now);
        Set<Long> eventIds = advertisements.stream().map(Advertisement::getEventId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, Event> eventsById = eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        return advertisements.stream().sorted(activeComparator(eventsById))
                .map(AdvertisementDtos.Response::from).toList();
    }

    public AdvertisementDtos.PricingResponse getPricing() {
        return new AdvertisementDtos.PricingResponse(eventAdPrice, BigDecimal.ZERO,
                paymentExpiry.toMinutes());
    }

    @Transactional
    public AdvertisementDtos.Response createEventAd(Long eventId, AdvertisementDtos.SaveRequest request,
            AuthenticatedMemberDto actor) {
        Event event = getEvent(eventId);
        if (!event.getOrganizerOrganizationId().equals(request.applicantOrganizationId()))
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        requireOrganizationManager(request.applicantOrganizationId(), actor);
        fileService.assertPublicAccessible(request.bannerFileId(), actor.getMemberId());
        validatePeriod(request.startAt(), request.endAt());
        OffsetDateTime now = OffsetDateTime.now();
        Advertisement ad = build(eventId, null, request, AdvertisementStatus.PAYMENT_PENDING, now);
        advertisementRepository.save(ad);
        Member buyer = memberRepository.findById(actor.getMemberId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        PaymentOrder order = paymentOrderRepository.save(PaymentOrder.createEventAdOrder(
                orderNoGenerator.generate(), buyer.getId(), buyer.getNickname(), buyer.getEmail(),
                eventAdPrice, now.plus(paymentExpiry), now));
        ad.assignPaymentOrder(order.getId(), now);
        return AdvertisementDtos.Response.from(ad,
                new AdvertisementDtos.PaymentOrderSummary(order.getOrderNo(), order.getTotalAmount()));
    }

    @Transactional
    public AdvertisementDtos.Response createBoothAd(Long boothId, AdvertisementDtos.SaveRequest request,
            AuthenticatedMemberDto actor) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        if (!request.applicantOrganizationId().equals(booth.getAssignedOrganizationId()))
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        requireOrganizationManager(request.applicantOrganizationId(), actor);
        fileService.assertPublicAccessible(request.bannerFileId(), actor.getMemberId());
        validatePeriod(request.startAt(), request.endAt());
        return save(null, boothId, request, AdvertisementStatus.REVIEW_PENDING);
    }

    public Page<AdvertisementDtos.Response> findOrganizationAds(Long organizationId,
            AuthenticatedMemberDto actor, Pageable pageable) {
        requireOrganizationMember(organizationId, actor);
        Specification<Advertisement> spec = (root, query, cb) ->
                cb.equal(root.get("applicantOrganizationId"), organizationId);
        return responses(advertisementRepository.findAll(spec, pageable));
    }

    public AdvertisementDtos.Response get(Long id, AuthenticatedMemberDto actor) {
        Advertisement ad = getAd(id); requireOwnerOrAdmin(ad, actor);
        return response(ad);
    }

    @Transactional
    public AdvertisementDtos.Response update(Long id, AdvertisementDtos.UpdateRequest request,
            AuthenticatedMemberDto actor) {
        Advertisement ad = getAd(id); requireOrganizationManager(ad.getApplicantOrganizationId(), actor);
        fileService.assertPublicAccessible(request.bannerFileId(), actor.getMemberId());
        validatePeriod(request.startAt(), request.endAt());
        transition(() -> ad.update(request.bannerFileId(), request.adText(), request.startAt(),
                request.endAt(), OffsetDateTime.now()));
        return response(ad);
    }

    @Transactional
    public AdvertisementDtos.Response updateCreative(Long id,
            AdvertisementDtos.CreativeUpdateRequest request, AuthenticatedMemberDto actor) {
        Advertisement ad = getAd(id);
        requireOrganizationManager(ad.getApplicantOrganizationId(), actor);
        fileService.assertPublicAccessible(request.bannerFileId(), actor.getMemberId());
        transition(() -> ad.updateCreative(request.bannerFileId(), request.adText(),
                OffsetDateTime.now()));
        return response(ad);
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
        return responses(advertisementRepository.findAll(spec, pageable));
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
        Advertisement ad = build(eventId, boothId, request, status, now);
        return AdvertisementDtos.Response.from(advertisementRepository.save(ad));
    }

    private Advertisement build(Long eventId, Long boothId,
            AdvertisementDtos.SaveRequest request, AdvertisementStatus status, OffsetDateTime now) {
        return Advertisement.builder().eventId(eventId).boothId(boothId)
                .applicantOrganizationId(request.applicantOrganizationId())
                .bannerFileId(request.bannerFileId()).adText(request.adText())
                .startAt(request.startAt()).endAt(request.endAt()).status(status)
                .createdAt(now).updatedAt(now).build();
    }

    private Comparator<Advertisement> activeComparator(Map<Long, Event> eventsById) {
        return Comparator.comparing((Advertisement ad) -> {
            if (ad.getEventId() == null) return OffsetDateTime.MAX;
            Event event = eventsById.get(ad.getEventId());
            return event == null ? OffsetDateTime.MAX : event.getStartAt();
        }).thenComparing(Advertisement::getId);
    }
    private AdvertisementDtos.Response response(Advertisement ad) {
        if (ad.getPaymentOrderId() == null) return AdvertisementDtos.Response.from(ad);
        return paymentOrderRepository.findById(ad.getPaymentOrderId())
                .map(order -> AdvertisementDtos.Response.from(ad,
                        new AdvertisementDtos.PaymentOrderSummary(order.getOrderNo(), order.getTotalAmount())))
                .orElseGet(() -> AdvertisementDtos.Response.from(ad));
    }
    private Page<AdvertisementDtos.Response> responses(Page<Advertisement> advertisements) {
        Set<Long> paymentOrderIds = advertisements.getContent().stream()
                .map(Advertisement::getPaymentOrderId).filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, PaymentOrder> ordersById = paymentOrderRepository.findAllById(paymentOrderIds).stream()
                .collect(Collectors.toMap(PaymentOrder::getId, Function.identity()));
        return advertisements.map(ad -> {
            PaymentOrder order = ordersById.get(ad.getPaymentOrderId());
            return order == null ? AdvertisementDtos.Response.from(ad)
                    : AdvertisementDtos.Response.from(ad,
                            new AdvertisementDtos.PaymentOrderSummary(
                                    order.getOrderNo(), order.getTotalAmount()));
        });
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
