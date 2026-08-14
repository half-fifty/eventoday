package com.min.edu.booth.congestion.dto.response;

import com.min.edu.booth.congestion.domain.BoothCongestion;
import com.min.edu.booth.congestion.domain.CongestionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoothCongestionResponse {
    private Long boothId;
    private String boothName;  // displayName이 있으면 그것, 없으면 boothCode
    private CongestionLevel congestionLevel;
    private Integer estimatedWaitTime;
    private BigDecimal predictedCapacityRate;
    private LocalDateTime recordedAt;

    public static BoothCongestionResponse from(BoothCongestion congestion) {
        var booth = congestion.getBooth();

        // displayName이 있고 비어있지 않으면 displayName 사용, 아니면 boothCode 사용
        String boothName = (booth.getDisplayName() != null && !booth.getDisplayName().isEmpty())
                ? booth.getDisplayName()
                : booth.getBoothCode();

        return BoothCongestionResponse.builder()
                .boothId(booth.getId())
                .boothName(boothName)
                .congestionLevel(congestion.getCongestionLevel())
                .estimatedWaitTime(congestion.getEstimatedWaitTime())
                .predictedCapacityRate(congestion.getPredictedCapacityRate())
                .recordedAt(congestion.getRecordedAt())
                .build();
    }
}