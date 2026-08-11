package com.min.edu.admin.service;

import com.min.edu.admin.dto.PlatformAdminDtos;
import com.min.edu.advertisement.domain.Advertisement;
import com.min.edu.advertisement.domain.AdvertisementStatus;
import com.min.edu.advertisement.repository.AdvertisementRepository;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.MemberStatus;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.repository.OrganizationMemberRepository;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlatformAdminService {
    private final MemberRepository memberRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EventRepository eventRepository;
    private final AdvertisementRepository advertisementRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final PaymentOrderRepository paymentOrderRepository;

    public PlatformAdminService(MemberRepository memberRepository,
            OrganizationMemberRepository organizationMemberRepository,
            JdbcTemplate jdbcTemplate, EventRepository eventRepository,
            AdvertisementRepository advertisementRepository, TicketOrderRepository ticketOrderRepository,
            PaymentOrderRepository paymentOrderRepository) {
        this.memberRepository = memberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.eventRepository = eventRepository;
        this.advertisementRepository = advertisementRepository;
        this.ticketOrderRepository = ticketOrderRepository;
        this.paymentOrderRepository = paymentOrderRepository;
    }

    public List<PlatformAdminDtos.Account> accounts(AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        Map<Long, OrganizationInfo> organizations = jdbcTemplate.query(
            "SELECT id, name, organization_type FROM organizations",
            (resultSet, rowNumber) -> new OrganizationInfo(
                resultSet.getLong("id"), resultSet.getString("name"),
                resultSet.getString("organization_type")))
            .stream().collect(Collectors.toMap(OrganizationInfo::id, Function.identity()));
        Map<Long, OrganizationMember> memberships = organizationMemberRepository.findAll().stream()
            .filter(item -> item.getStatus() == OrganizationMemberStatus.ACTIVE)
            .collect(Collectors.toMap(OrganizationMember::getMemberId, Function.identity(), (first, ignored) -> first));
        return memberRepository.findAll().stream()
            .sorted(Comparator.comparing(Member::getCreatedAt).reversed())
            .map(member -> {
                OrganizationMember membership = memberships.get(member.getId());
                OrganizationInfo organization = membership == null ? null : organizations.get(membership.getOrganizationId());
                return new PlatformAdminDtos.Account(member.getId(), member.getEmail(), member.getNickname(),
                    member.getPlatformRole().name(), member.getStatus().name(),
                    organization == null ? null : organization.name(),
                    organization == null ? null : organization.type(),
                    member.getCreatedAt(), member.getLastLoginAt());
            }).toList();
    }

    @Transactional
    public PlatformAdminDtos.Account changeAccountStatus(Long memberId, String requestedStatus,
            AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        MemberStatus status;
        try { status = MemberStatus.valueOf(requestedStatus); }
        catch (RuntimeException exception) { throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE); }
        if (memberId.equals(actor.getMemberId()) && status != MemberStatus.ACTIVE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        member.changeStatus(status, OffsetDateTime.now());
        return new PlatformAdminDtos.Account(member.getId(), member.getEmail(), member.getNickname(),
            member.getPlatformRole().name(), member.getStatus().name(), null, null,
            member.getCreatedAt(), member.getLastLoginAt());
    }

    public PlatformAdminDtos.Statistics statistics(AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        List<Event> events = eventRepository.findAll();
        long activeEvents = events.stream().filter(event -> event.getStatus() == EventStatus.PUBLISHED).count();
        long tickets = ticketOrderRepository.findAll().stream()
            .filter(order -> TicketOrderStatus.CONFIRMED.name().equals(order.getStatus()))
            .mapToLong(TicketOrder::getTotalQuantity).sum();
        Long exhibitorCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM organizations WHERE organization_type = 'EXHIBITOR'", Long.class);
        long exhibitors = exhibitorCount == null ? 0L : exhibitorCount;
        Map<Long, PaymentOrder> paymentOrders = paymentOrderRepository.findAll().stream()
            .collect(Collectors.toMap(PaymentOrder::getId, Function.identity()));
        BigDecimal revenue = advertisementRepository.findAll().stream()
            .map(Advertisement::getPaymentOrderId).filter(id -> id != null)
            .map(paymentOrders::get).filter(order -> order != null
                && order.getOrderType() == PaymentOrderType.EVENT_AD
                && PaymentOrderStatus.PAID.name().equals(order.getStatus()))
            .map(PaymentOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PlatformAdminDtos.Statistics(activeEvents, tickets, exhibitors, revenue);
    }

    public List<PlatformAdminDtos.AuditEntry> audit(AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        List<PlatformAdminDtos.AuditEntry> entries = new ArrayList<>();
        for (Event event : eventRepository.findAll()) {
            if (List.of(EventStatus.APPROVED, EventStatus.REJECTED, EventStatus.PUBLISHED,
                    EventStatus.SUSPENDED, EventStatus.CANCELLED).contains(event.getStatus())) {
                entries.add(new PlatformAdminDtos.AuditEntry("event-" + event.getId(), "EVENT",
                    event.getStatus().name(), event.getName(), event.getRejectionReason(), event.getUpdatedAt()));
            }
        }
        for (Advertisement ad : advertisementRepository.findAll()) {
            if (ad.getReviewedBy() != null) {
                entries.add(new PlatformAdminDtos.AuditEntry("advertisement-" + ad.getId(), "ADVERTISEMENT",
                    ad.getStatus().name(), ad.getEventId() != null ? "행사 광고 #" + ad.getEventId() : "부스 광고 #" + ad.getBoothId(),
                    ad.getRejectionReason(), ad.getUpdatedAt()));
            }
        }
        return entries.stream().filter(entry -> entry.occurredAt() != null)
            .sorted(Comparator.comparing(PlatformAdminDtos.AuditEntry::occurredAt).reversed())
            .limit(100).toList();
    }

    public PlatformAdminDtos.Dashboard dashboard(AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        long pendingEvents = eventRepository.findAll().stream()
            .filter(event -> event.getStatus() == EventStatus.SUBMITTED || event.getStatus() == EventStatus.UNDER_REVIEW).count();
        long activeAccounts = memberRepository.findAll().stream().filter(member -> member.getStatus() == MemberStatus.ACTIVE).count();
        long pendingAds = advertisementRepository.findAll().stream()
            .filter(ad -> ad.getStatus() == AdvertisementStatus.REVIEW_PENDING
                || ad.getStatus() == AdvertisementStatus.PAID).count();
        PlatformAdminDtos.Statistics stats = statistics(actor);
        return new PlatformAdminDtos.Dashboard(pendingEvents, activeAccounts, pendingAds,
            stats.activeEventCount(), audit(actor).stream().limit(5).toList());
    }

    private void requireAdmin(AuthenticatedMemberDto actor) {
        if (actor == null || actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private record OrganizationInfo(Long id, String name, String type) {}
}
