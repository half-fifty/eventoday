package com.min.edu.booth.congestion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastListResponse {
    private LocalDate forecastDate;
    private List<CongestionForecastResponse> forecasts;
    private CongestionForecastResponse bestTimeSlot;  // 가장 한산한 시간대
}