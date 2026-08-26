package com.min.edu.booth.congestion.service;

import com.min.edu.booth.congestion.domain.BoothCongestion;
import com.min.edu.booth.congestion.domain.BoothCongestionForecast;
import com.min.edu.booth.congestion.domain.CongestionLevel;
import com.min.edu.booth.congestion.repository.BoothCongestionRepository;
import com.min.edu.booth.congestion.repository.BoothCongestionForecastRepository;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.repository.BoothQrScanRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothCongestionService {

    private static final int RANKING_LIMIT = 10;

    private final BoothCongestionRepository boothCongestionRepository;
    private final BoothCongestionForecastRepository boothCongestionForecastRepository;
    private final BoothRepository boothRepository;
    private final BoothQrScanRepository boothQrScanRepository;
    private final BoothCongestionCalculator congestionCalculator;

    /**
     * 특정 부스의 현재 혼잡도 — 최근 10분 QR 스캔 수를 실시간으로 집계해서 계산한다.
     * (예전엔 booth_congestion 테이블에서 조회했는데, 그 테이블에 값을 채워 넣는 호출부가 코드
     * 어디에도 없어서 이 엔드포인트가 모든 부스에서 항상 404를 반환하고 있었다. 평면도 혼잡도
     * 마커(VenueMapCongestionService)가 이미 쓰고 있는 것과 같은 QR 스캔 기반 계산으로 통일한다.)
     */
    public BoothCongestion getLatestCongestion(Long boothId) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.BOOTH_NOT_FOUND));

        long recentScans = boothQrScanRepository.countByBoothIdAndScannedAtAfter(boothId, windowStart());

        return BoothCongestion.builder()
                .booth(booth)
                .congestionLevel(congestionCalculator.evaluate(recentScans))
                .recordedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 이벤트의 인기 부스 조회 (최근 10분 QR 스캔 많은 순, 상위 10개)
     */
    public List<BoothCongestion> getPopularBooths(Long eventId) {
        var rows = boothQrScanRepository.findPopularBooths(eventId, windowStart(), PageRequest.of(0, RANKING_LIMIT));
        return toCongestionList(rows.getContent());
    }

    /**
     * 이벤트의 한산한 부스 조회 (혼잡도 LOW 등급인 부스만, 스캔 적은 순 상위 10개) — 스캔이 0건인
     * 부스도 후보에 포함한다. 정렬 자체는 스캔 수 오름차순 쿼리를 쓰지만, 그 상위 몇 개가 전부
     * HIGH/MEDIUM일 수도 있으므로(예: 모든 부스가 붐비는 시간대) 실제로 LOW 등급인 것만 남긴다 —
     * 그래야 "한산한 부스" 목록이 실제로는 안 한산한 부스를 추천하는 일이 없다.
     */
    public List<BoothCongestion> getUncrowdedBooths(Long eventId) {
        var rows = boothQrScanRepository.findAllBoothsWithCongestion(
                eventId, windowStart(), PageRequest.of(0, RANKING_LIMIT));
        return toCongestionList(rows.getContent()).stream()
                .filter(congestion -> congestion.getCongestionLevel() == CongestionLevel.LOW)
                .toList();
    }

    private OffsetDateTime windowStart() {
        return OffsetDateTime.now().minusMinutes(BoothCongestionCalculator.WINDOW_MINUTES);
    }

    // [boothId, scanCount] 행 목록을 부스 정보와 합쳐 BoothCongestion(비영속) 목록으로 만든다.
    private List<BoothCongestion> toCongestionList(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> boothIds = rows.stream().map(row -> ((Number) row[0]).longValue()).toList();
        Map<Long, Booth> boothsById = boothRepository.findAllById(boothIds).stream()
                .collect(Collectors.toMap(Booth::getId, Function.identity()));

        return rows.stream()
                .map(row -> {
                    Booth booth = boothsById.get(((Number) row[0]).longValue());
                    if (booth == null) {
                        return null;
                    }
                    long scanCount = ((Number) row[1]).longValue();
                    return BoothCongestion.builder()
                            .booth(booth)
                            .congestionLevel(congestionCalculator.evaluate(scanCount))
                            .recordedAt(LocalDateTime.now())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 혼잡도 데이터 저장 (실시간 업데이트)
     */
    @Transactional
    public BoothCongestion saveCongestion(
            Long boothId,
            CongestionLevel level,
            Integer waitTime,
            java.math.BigDecimal capacityRate
    ) {
        if (level == null
                || (waitTime != null && waitTime < 0)
                || (capacityRate != null
                    && (capacityRate.signum() < 0
                        || capacityRate.compareTo(java.math.BigDecimal.valueOf(100)) > 0))) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.BOOTH_NOT_FOUND));

        BoothCongestion congestion = BoothCongestion.builder()
                .booth(booth)
                .congestionLevel(level)
                .estimatedWaitTime(waitTime)
                .predictedCapacityRate(capacityRate)
                .recordedAt(LocalDateTime.now())
                .build();

        return boothCongestionRepository.save(congestion);
    }

    /**
     * 혼잡도 레벨에 따른 점수 계산 (0~1)
     */
    public double calculateCongestionScore(CongestionLevel level) {
        return switch(level) {
            case LOW -> 1.0;
            case MEDIUM -> 0.6;
            case HIGH -> 0.2;
        };
    }
}