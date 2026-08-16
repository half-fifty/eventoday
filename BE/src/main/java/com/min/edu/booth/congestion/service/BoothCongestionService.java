package com.min.edu.booth.congestion.service;

import com.min.edu.booth.congestion.domain.BoothCongestion;
import com.min.edu.booth.congestion.domain.BoothCongestionForecast;
import com.min.edu.booth.congestion.domain.CongestionLevel;
import com.min.edu.booth.congestion.repository.BoothCongestionRepository;
import com.min.edu.booth.congestion.repository.BoothCongestionForecastRepository;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothCongestionService {

    private final BoothCongestionRepository boothCongestionRepository;
    private final BoothCongestionForecastRepository boothCongestionForecastRepository;
    private final BoothRepository boothRepository;

    /**
     * 특정 부스의 최신 혼잡도 조회
     */
    public BoothCongestion getLatestCongestion(Long boothId) {
        Optional<BoothCongestion> latestCongestion = boothCongestionRepository.findLatestByBoothId(boothId);
        if (latestCongestion.isPresent()) {
            return latestCongestion.get();
        }
        if (!boothRepository.existsById(boothId)) {
            throw new BusinessException(GlobalErrorCode.BOOTH_NOT_FOUND);
        }
        throw new BusinessException(GlobalErrorCode.BOOTH_CONGESTION_NOT_FOUND);
    }

    /**
     * 이벤트의 인기 부스 조회 (혼잡도 높은 순서, 상위 10개)
     */
    public List<BoothCongestion> getPopularBooths(Long eventId) {
        List<BoothCongestion> popularBooths = boothCongestionRepository.findPopularBoothsByEvent(eventId);
        return popularBooths.stream()
                .limit(10)
                .toList();
    }

    /**
     * 이벤트의 한산한 부스 조회 (혼잡도 낮은 순서, 상위 10개)
     */
    public List<BoothCongestion> getUncrowdedBooths(Long eventId) {
        List<BoothCongestion> uncrowdedBooths = boothCongestionRepository.findUncrowdedBoothsByEvent(eventId);
        return uncrowdedBooths.stream()
                .limit(10)
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