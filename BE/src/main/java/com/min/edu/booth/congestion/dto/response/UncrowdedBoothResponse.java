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
public class UncrowdedBoothResponse {
    private Long boothId;
    private String boothName;
    private CongestionLevel congestionLevel;
    private Integer estimatedWaitTime;
    private BigDecimal capacityRate;
    private String reason;

    public static UncrowdedBoothResponse from(BoothCongestion congestion) {
        String reason = buildReason(congestion);

        return UncrowdedBoothResponse.builder()
                .boothId(congestion.getBooth().getId())
                .boothName(getBoothName(congestion.getBooth()))  // ← 수정
                .congestionLevel(congestion.getCongestionLevel())
                .estimatedWaitTime(congestion.getEstimatedWaitTime())
                .capacityRate(congestion.getPredictedCapacityRate())
                .reason(reason)
                .build();
    }

    private static String getBoothName(com.min.edu.booth.domain.Booth booth) {
        // displayName이 있으면 사용, 없으면 boothCode 사용
        if (booth.getDisplayName() != null && !booth.getDisplayName().isEmpty()) {
            return booth.getDisplayName();
        }
        return booth.getBoothCode();
    }

    private static String buildReason(BoothCongestion congestion) {
        if (congestion.getEstimatedWaitTime() != null && congestion.getEstimatedWaitTime() < 5) {
            return "현재 대기시간이 매우 짧습니다! ⚡";
        }
        return "현재 혼잡도가 낮습니다. 🎯";
    }
}