package com.min.edu.booth.congestion.service;

import com.min.edu.booth.congestion.domain.CongestionLevel;
import org.springframework.stereotype.Component;

// 최근 QR 스캔 수를 혼잡도 등급으로 변환하는 기준을 한 곳에 모은다. 이전엔 BoothCongestionService와
// VenueMapCongestionService가 같은 개념("혼잡도")을 각자 다른 곳에서 하드코딩해서, 부스 상세 화면과
// 평면도 화면의 혼잡도가 서로 어긋나 보일 수 있었다.
@Component
public class BoothCongestionCalculator {

    public static final int WINDOW_MINUTES = 10;

    private static final long HIGH_THRESHOLD = 20;
    private static final long MEDIUM_THRESHOLD = 10;

    public CongestionLevel evaluate(long recentScanCount) {
        if (recentScanCount >= HIGH_THRESHOLD) {
            return CongestionLevel.HIGH;
        }
        if (recentScanCount >= MEDIUM_THRESHOLD) {
            return CongestionLevel.MEDIUM;
        }
        return CongestionLevel.LOW;
    }
}
