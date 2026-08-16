package com.min.edu.booth.congestion.service;

import com.min.edu.booth.congestion.domain.BoothCongestionForecast;
import com.min.edu.booth.congestion.domain.CongestionLevel;
import com.min.edu.booth.congestion.repository.BoothCongestionForecastRepository;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothCongestionForecastService {

    private final BoothCongestionForecastRepository boothCongestionForecastRepository;
    private final BoothRepository boothRepository;

    /**
     * 부스의 시간대별 혼잡도 예측 조회
     */
    public List<BoothCongestionForecast> getForecastByDate(Long boothId, LocalDate forecastDate) {
        requireBoothExists(boothId);
        return boothCongestionForecastRepository.findByBoothIdAndForecastDate(boothId, forecastDate);
    }

    /**
     * 부스의 한산한 시간대 조회 (LOW 혼잡도)
     */
    public List<BoothCongestionForecast> getBestTimeSlots(Long boothId, LocalDate forecastDate) {
        requireBoothExists(boothId);
        return boothCongestionForecastRepository.findBestTimeSlots(boothId, forecastDate);
    }

    /**
     * 부스의 최적 시간대 1개 조회
     */
    public Optional<BoothCongestionForecast> getBestTimeSlot(Long boothId, LocalDate forecastDate) {
        requireBoothExists(boothId);
        return boothCongestionForecastRepository.findBestTimeSlot(boothId, forecastDate);
    }

    private void requireBoothExists(Long boothId) {
        if (!boothRepository.existsById(boothId)) {
            throw new BusinessException(GlobalErrorCode.BOOTH_NOT_FOUND);
        }
    }

    /**
     * 혼잡도 예측 데이터 저장
     */
    @Transactional
    public BoothCongestionForecast saveForecast(
            Long boothId,
            Integer forecastHour,
            LocalDate forecastDate,
            CongestionLevel predictedLevel,
            Integer waitTime,
            BigDecimal capacityRate
    ) {
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.BOOTH_NOT_FOUND));

        BoothCongestionForecast forecast = BoothCongestionForecast.builder()
                .booth(booth)
                .forecastHour(forecastHour)
                .forecastDate(forecastDate)
                .predictedCongestionLevel(predictedLevel)
                .predictedWaitTime(waitTime)
                .predictedCapacityRate(capacityRate)
                .createdAt(LocalDateTime.now())
                .build();

        return boothCongestionForecastRepository.save(forecast);
    }

    /**
     * 해당 날짜의 예측 데이터 일괄 저장 (배치 작업용)
     */
    @Transactional
    public void saveForecastBatch(Long boothId, LocalDate forecastDate, List<BoothCongestionForecast> forecasts) {
        requireBoothExists(boothId);

        boolean mismatched = forecasts.stream().anyMatch(forecast ->
                !boothId.equals(forecast.getBooth().getId()) || !forecastDate.equals(forecast.getForecastDate()));
        if (mismatched) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        boothCongestionForecastRepository.saveAll(forecasts);
    }
}