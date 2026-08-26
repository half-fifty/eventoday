package com.min.edu.booth.ai.dto;

import java.math.BigDecimal;

// Gemini의 [y0,x0,y1,x1] bounding box를 부스 위치 하나(중심점)로 환산한 결과.
public record DetectedBoothBox(String label, BigDecimal xRatio, BigDecimal yRatio) {
}
