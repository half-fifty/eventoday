package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueMapWithCongestionResponseDto {

    private Long venueMapId;
    private String floorName;
    private Integer originalWidth;
    private Integer originalHeight;
    private List<BoothMarker> positions;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BoothMarker {
        private Long boothId;
        private String boothName;
        private Double xRatio;
        private Double yRatio;
        private Long congestionCount;      // 최근 10분 방문자 수
        private String congestionLevel;    // "HIGH", "MEDIUM", "LOW"
        private String color;              // "#FF0000" (빨강/노랑/초록)
    }
}