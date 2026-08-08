package com.min.edu.booth.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothHourlyStatistics;
import com.min.edu.booth.dto.BoothStatisticsDtos;
import com.min.edu.booth.repository.BoothHourlyStatisticsRepository;
import com.min.edu.booth.repository.BoothOrganizationMemberRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BoothStatisticsService {

    private final BoothRepository boothRepository;
    private final BoothHourlyStatisticsRepository boothHourlyStatisticsRepository;
    private final EventMemberRepository eventMemberRepository;
    private final BoothOrganizationMemberRepository boothOrganizationMemberRepository;

    /**
     * 시간대별 부스 통계 조회 (STAT-API-001)
     *
     * 접근 권한:
     * - PLATFORM_ADMIN: 전체 허용
     * - EVENT_MANAGER: 해당 행사 관리자
     * - ORG_MEMBER: 해당 부스에 배정된 참가기업 조직원
     *
     * @param boothId 부스 ID
     * @param date    조회 날짜 (null이면 오늘)
     * @param member  인증 회원
     */
    public BoothStatisticsDtos.HourlySummary getHourlyStatistics(
            Long boothId, LocalDate date, AuthenticatedMemberDto member) {

        // 비로그인 401 처리
        if (member == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        // 부스 존재 여부 확인
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 권한 검증
        requireStatisticsAccess(booth, member);

        // 시간대별 통계 조회 (stat_hour 오름차순)
        List<BoothHourlyStatistics> stats =
                boothHourlyStatisticsRepository.findByBoothIdAndStatDateOrderByStatHourAsc(boothId, date);

        List<BoothStatisticsDtos.HourlyEntry> hourlyEntries = stats.stream()
                .map(BoothStatisticsDtos.HourlyEntry::from)
                .toList();

        return new BoothStatisticsDtos.HourlySummary(boothId, date, hourlyEntries);
    }

    /**
     * 통계 조회 권한 검증 헬퍼
     * - PLATFORM_ADMIN: 전체 허용
     * - EVENT_MANAGER: 해당 행사 관리자
     * - ORG_MEMBER: 부스에 배정된 참가기업 조직원 (OWNER/MANAGER/STAFF)
     */
    private void requireStatisticsAccess(Booth booth, AuthenticatedMemberDto member) {
        // PLATFORM_ADMIN
        if (member.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return;
        }

        // EVENT_MANAGER
        if (eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                booth.getEventId(), member.getMemberId(), EventRole.EVENT_MANAGER)) {
            return;
        }

        // 배정된 참가기업 조직원 (부스가 배정된 경우에만 체크)
        if (booth.getAssignedOrganizationId() != null
                && boothOrganizationMemberRepository
                .existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                        booth.getAssignedOrganizationId(),
                        member.getMemberId(),
                        OrganizationMemberStatus.ACTIVE,
                        List.of(OrganizationRole.OWNER, OrganizationRole.MANAGER, OrganizationRole.STAFF))) {
            return;
        }

        throw new BusinessException(GlobalErrorCode.FORBIDDEN);
    }
}