package com.min.edu.booth.repository;

// 기간별 부스 통계 집계 Projection (STAT-API-003, 004 공통 사용)
public interface BoothStatAggregation {

    Long getBoothId();
    Long getTotalReservationCount();
    Long getTotalNoShowCount();
    Long getTotalQrScanCount();
}
