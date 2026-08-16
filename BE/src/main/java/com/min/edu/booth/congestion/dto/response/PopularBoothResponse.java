package com.min.edu.booth.congestion.dto.response;

import com.min.edu.booth.congestion.domain.BoothCongestion;
import com.min.edu.booth.congestion.domain.CongestionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularBoothResponse {
    private Long boothId;
    private String boothName;
    private CongestionLevel congestionLevel;
    private Integer estimatedWaitTime;
    private BigDecimal capacityRate;
    private Integer rank;

    public static PopularBoothResponse from(BoothCongestion congestion, Integer rank) {
        return PopularBoothResponse.builder()
                .boothId(congestion.getBooth().getId())
                .boothName(getBoothName(congestion.getBooth()))  // ← 수정
                .congestionLevel(congestion.getCongestionLevel())
                .estimatedWaitTime(congestion.getEstimatedWaitTime())
                .capacityRate(congestion.getPredictedCapacityRate())
                .rank(rank)
                .build();
    }

    private static String getBoothName(com.min.edu.booth.domain.Booth booth) {
        // displayName이 있으면 사용, 없으면 boothCode 사용
        if (booth.getDisplayName() != null && !booth.getDisplayName().isEmpty()) {
            return booth.getDisplayName();
        }
        return booth.getBoothCode();
    }
}