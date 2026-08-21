package com.min.edu.booth.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

// Gemini Vision이 제안한 부스 위치 하나. 저장되지 않은 "제안"이며, 관리자가 확인 후
// 기존 upsertPositions API로 확정해야 실제로 저장된다.
@Getter
@Builder
public class VenueMapAutoLayoutSuggestionDto {

    private String label;
    private Long boothId;
    private String boothCode;
    private boolean matched;
    private BigDecimal xRatio;
    private BigDecimal yRatio;
}
