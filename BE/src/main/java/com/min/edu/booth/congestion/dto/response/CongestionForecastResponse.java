package com.min.edu.booth.congestion.dto.response;

import com.min.edu.booth.congestion.domain.BoothCongestionForecast;
import com.min.edu.booth.congestion.domain.CongestionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CongestionForecastResponse {
    private Integer forecastHour;                   // 예측 시간 (0~23)
    private CongestionLevel predictedCongestionLevel;
    private Integer predictedWaitTime;
    private BigDecimal predictedCapacityRate;
    private String recommendation;                  // "14시 방문 추천! 🎯" 등

    public static CongestionForecastResponse from(BoothCongestionForecast forecast) {
        String recommendation = buildRecommendation(forecast);

        return CongestionForecastResponse.builder()
                .forecastHour(forecast.getForecastHour())
                .predictedCongestionLevel(forecast.getPredictedCongestionLevel())
                .predictedWaitTime(forecast.getPredictedWaitTime())
                .predictedCapacityRate(forecast.getPredictedCapacityRate())
                .recommendation(recommendation)
                .build();
    }

    private static String buildRecommendation(BoothCongestionForecast forecast) {
        return switch(forecast.getPredictedCongestionLevel()) {
            case LOW -> String.format("%d시 방문 추천! 🎯", forecast.getForecastHour());
            case MEDIUM -> String.format("%d시 중간 혼잡", forecast.getForecastHour());
            case HIGH -> String.format("%d시는 혼잡합니다", forecast.getForecastHour());
        };
    }
}