package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothQrScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface BoothQrScanRepository extends JpaRepository<BoothQrScan, Long> {

    // WBS-156: 특정 부스 최근 10분 혼잡도 조회
    @Query("SELECT COUNT(bqs) FROM BoothQrScan bqs " +
            "WHERE bqs.boothId = :boothId " +
            "AND bqs.scannedAt >= :since " +
            "AND bqs.duplicate = false")
    long countByBoothIdAndScannedAtAfter(
            @Param("boothId") Long boothId,
            @Param("since") OffsetDateTime since
    );

    // WBS-157, 162: 행사 전체 부스 혼잡도 (인기 부스 상위 N개)
    @Query("SELECT bqs.boothId, COUNT(bqs) as congestionCount " +
            "FROM BoothQrScan bqs " +
            "JOIN Booth b ON bqs.boothId = b.id " +
            "WHERE b.eventId = :eventId " +
            "AND bqs.scannedAt >= :since " +
            "AND bqs.duplicate = false " +
            "GROUP BY bqs.boothId " +
            "ORDER BY congestionCount DESC")
    Page<Object[]> findPopularBooths(
            @Param("eventId") Long eventId,  // ← 추가!
            @Param("since") OffsetDateTime since,
            Pageable pageable
    );

    // WBS-155: 중복 스캔 판별 (같은 교환 코드가 이미 스캔됐는지)
    boolean existsByExchangeCodeId(Long exchangeCodeId);
}