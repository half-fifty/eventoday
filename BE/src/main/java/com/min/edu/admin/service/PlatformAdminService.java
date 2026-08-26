package com.min.edu.admin.service;

import com.min.edu.admin.dto.PlatformAdminDtos;
import com.min.edu.admin.domain.PlatformAuditLog;
import com.min.edu.admin.repository.PlatformAuditLogRepository;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.Member;
import com.min.edu.member.domain.MemberStatus;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.member.repository.MemberRepository;
import com.min.edu.organization.domain.OrganizationMember;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.repository.OrganizationMemberRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlatformAdminService {
    private final MemberRepository memberRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformAuditLogRepository auditLogRepository;
    private final PlatformAuditService auditService;

    public PlatformAdminService(MemberRepository memberRepository,
            OrganizationMemberRepository organizationMemberRepository,
            JdbcTemplate jdbcTemplate, PlatformAuditLogRepository auditLogRepository,
            PlatformAuditService auditService) {
        this.memberRepository = memberRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
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
        auditService.record(actor.getMemberId(), "ACCOUNT", "STATUS_" + status.name(),
            member.getId(), member.getNickname(), null);
        return new PlatformAdminDtos.Account(member.getId(), member.getEmail(), member.getNickname(),
            member.getPlatformRole().name(), member.getStatus().name(), null, null,
            member.getCreatedAt(), member.getLastLoginAt());
    }

    public PlatformAdminDtos.Statistics statistics(AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        Long activeEvents = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events WHERE status = 'PUBLISHED'", Long.class);
        Long tickets = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(total_quantity), 0) FROM ticket_orders WHERE status = 'CONFIRMED'", Long.class);
        Long exhibitors = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM organizations WHERE organization_type = 'EXHIBITOR'", Long.class);
        BigDecimal revenue = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(po.total_amount), 0) FROM advertisements a
            JOIN payment_orders po ON po.id = a.payment_order_id
            WHERE po.order_type = 'EVENT_AD' AND po.status = 'PAID'
            """, BigDecimal.class);
        return new PlatformAdminDtos.Statistics(value(activeEvents), value(tickets), value(exhibitors),
            revenue == null ? BigDecimal.ZERO : revenue);
    }

    public List<PlatformAdminDtos.AuditEntry> audit(AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        return auditEntries(100);
    }

    public PlatformAdminDtos.Dashboard dashboard(AuthenticatedMemberDto actor) {
        requireAdmin(actor);
        Long pendingEvents = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE status IN ('SUBMITTED', 'UNDER_REVIEW')", Long.class);
        Long activeAccounts = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM members WHERE status = 'ACTIVE'", Long.class);
        Long pendingAds = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM advertisements WHERE status IN ('PAID', 'REVIEW_PENDING')", Long.class);
        Long activeEvents = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE status = 'PUBLISHED'", Long.class);
        return new PlatformAdminDtos.Dashboard(value(pendingEvents), value(activeAccounts),
            value(pendingAds), value(activeEvents), auditEntries(5));
    }

    private void requireAdmin(AuthenticatedMemberDto actor) {
        if (actor == null || actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }

    private List<PlatformAdminDtos.AuditEntry> auditEntries(int size) {
        return auditLogRepository.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(0, size)).stream()
            .map(this::auditEntry).toList();
    }

    private PlatformAdminDtos.AuditEntry auditEntry(PlatformAuditLog log) {
        return new PlatformAdminDtos.AuditEntry(String.valueOf(log.getId()), log.getCategory(),
            log.getAction(), log.getTargetName(), log.getDetail(), log.getOccurredAt());
    }

    private long value(Long number) { return number == null ? 0L : number; }

    private record OrganizationInfo(Long id, String name, String type) {}
}
