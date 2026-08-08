package com.min.edu.booth.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothHourlyStatistics;
import com.min.edu.booth.dto.BoothStatisticsDtos;
import com.min.edu.booth.repository.BoothHourlyStatisticsRepository;
import com.min.edu.booth.repository.BoothOrganizationMemberRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothStatAggregation;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final EventRepository eventRepository;
    private final BoothOrganizationMemberRepository boothOrganizationMemberRepository;

    /**
     * 시간대별 부스 통계 조회
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
     * 전날 부스 통계 조회
     *
     * 전날(LocalDate.now() - 1일) 시간대별 통계를 합산하여 반환
     * 접근 권한은 STAT-API-001과 동일
     *
     * @param boothId 부스 ID
     * @param member  인증 회원
     */
    public BoothStatisticsDtos.PreviousDaySummary getPreviousDayStatistics(
            Long boothId, AuthenticatedMemberDto member) {

        // 비로그인 401 처리
        if (member == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        // 부스 존재 여부 확인
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 권한 검증
        requireStatisticsAccess(booth, member);

        // 전날 날짜 계산
        LocalDate yesterday = LocalDate.now().minusDays(1);

        // 전날 시간대별 통계 조회
        List<BoothHourlyStatistics> stats =
                boothHourlyStatisticsRepository.findByBoothIdAndStatDateOrderByStatHourAsc(boothId, yesterday);

        List<BoothStatisticsDtos.HourlyEntry> hourlyEntries = stats.stream()
                .map(BoothStatisticsDtos.HourlyEntry::from)
                .toList();

        // 시간대별 합산
        int totalReservation = stats.stream().mapToInt(BoothHourlyStatistics::getReservationCount).sum();
        int totalNoShow = stats.stream().mapToInt(BoothHourlyStatistics::getNoShowCount).sum();
        int totalQrScan = stats.stream().mapToInt(BoothHourlyStatistics::getQrScanCount).sum();

        return new BoothStatisticsDtos.PreviousDaySummary(
                boothId, yesterday,
                totalReservation, totalNoShow, totalQrScan,
                hourlyEntries
        );
    }

    /**
     * 기간별 인기 부스 통계 조회
     *
     * 기간 내 reservation_count 합계 기준 내림차순 순위 반환
     * 접근 권한: PLATFORM_ADMIN 또는 EVENT_MANAGER
     *
     * @param eventId 행사 ID
     * @param from    조회 시작일
     * @param to      조회 종료일
     * @param member  인증 회원
     */
    public BoothStatisticsDtos.PopularBoothsSummary getPopularBooths(
            Long eventId, LocalDate from, LocalDate to, AuthenticatedMemberDto member) {

        // 비로그인 401 처리
        if (member == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        // 행사 존재 여부 확인
        if (!eventRepository.existsById(eventId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        // EVENT_MANAGER 또는 PLATFORM_ADMIN만 접근 가능
        if (member.getPlatformRole() != PlatformRole.PLATFORM_ADMIN
                && !eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                        eventId, member.getMemberId(), EventRole.EVENT_MANAGER)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 행사 내 전체 부스 조회 → boothId → boothCode 맵 생성
        List<Booth> booths = boothRepository.findByEventId(eventId);
        Map<Long, String> boothCodeMap = booths.stream()
                .collect(Collectors.toMap(Booth::getId, Booth::getBoothCode));

        // 부스가 없으면 빈 결과 반환
        if (boothCodeMap.isEmpty()) {
            return new BoothStatisticsDtos.PopularBoothsSummary(eventId, from, to, List.of());
        }

        // 기간별 부스별 통계 합산 (예약수 내림차순)
        List<BoothStatAggregation> aggregations =
                boothHourlyStatisticsRepository.aggregateByBoothIdsAndDateBetween(
                        boothCodeMap.keySet(), from, to);

        // 순위 부여 후 응답 DTO 변환
        List<BoothStatisticsDtos.PopularBoothEntry> entries = new ArrayList<>();
        for (int i = 0; i < aggregations.size(); i++) {
            BoothStatAggregation agg = aggregations.get(i);
            entries.add(new BoothStatisticsDtos.PopularBoothEntry(
                    i + 1,
                    agg.getBoothId(),
                    boothCodeMap.getOrDefault(agg.getBoothId(), ""),
                    agg.getTotalReservationCount(),
                    agg.getTotalNoShowCount(),
                    agg.getTotalQrScanCount()
            ));
        }

        return new BoothStatisticsDtos.PopularBoothsSummary(eventId, from, to, entries);
    }

    /**
     * 행사 운영 통계 요약 조회
     *
     * 기간 내 행사 전체 합산 통계 + 부스별 통계 목록 반환
     * 접근 권한: PLATFORM_ADMIN 또는 EVENT_MANAGER
     *
     * @param eventId 행사 ID
     * @param from    조회 시작일
     * @param to      조회 종료일
     * @param member  인증 회원
     */
    public BoothStatisticsDtos.EventOverviewSummary getEventOverview(
            Long eventId, LocalDate from, LocalDate to, AuthenticatedMemberDto member) {

        // 비로그인 401 처리
        if (member == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        // 행사 존재 여부 확인
        if (!eventRepository.existsById(eventId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        // EVENT_MANAGER 또는 PLATFORM_ADMIN만 접근 가능
        if (member.getPlatformRole() != PlatformRole.PLATFORM_ADMIN
                && !eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                        eventId, member.getMemberId(), EventRole.EVENT_MANAGER)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 행사 내 전체 부스 조회 → boothId → boothCode 맵 생성
        List<Booth> booths = boothRepository.findByEventId(eventId);
        Map<Long, String> boothCodeMap = booths.stream()
                .collect(Collectors.toMap(Booth::getId, Booth::getBoothCode));

        // 부스가 없으면 빈 결과 반환
        if (boothCodeMap.isEmpty()) {
            return new BoothStatisticsDtos.EventOverviewSummary(eventId, from, to, 0, 0, 0, List.of());
        }

        // 기간별 부스별 통계 합산 (예약수 내림차순)
        List<BoothStatAggregation> aggregations =
                boothHourlyStatisticsRepository.aggregateByBoothIdsAndDateBetween(
                        boothCodeMap.keySet(), from, to);

        // 부스별 요약 DTO 변환
        List<BoothStatisticsDtos.BoothStatSummary> boothSummaries = aggregations.stream()
                .map(agg -> new BoothStatisticsDtos.BoothStatSummary(
                        agg.getBoothId(),
                        boothCodeMap.getOrDefault(agg.getBoothId(), ""),
                        agg.getTotalReservationCount(),
                        agg.getTotalNoShowCount(),
                        agg.getTotalQrScanCount()
                ))
                .toList();

        // 전체 합산
        long totalReservation = aggregations.stream()
                .mapToLong(BoothStatAggregation::getTotalReservationCount).sum();
        long totalNoShow = aggregations.stream()
                .mapToLong(BoothStatAggregation::getTotalNoShowCount).sum();
        long totalQrScan = aggregations.stream()
                .mapToLong(BoothStatAggregation::getTotalQrScanCount).sum();

        return new BoothStatisticsDtos.EventOverviewSummary(
                eventId, from, to,
                totalReservation, totalNoShow, totalQrScan,
                boothSummaries
        );
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