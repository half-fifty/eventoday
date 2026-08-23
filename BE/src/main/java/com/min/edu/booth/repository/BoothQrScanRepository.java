package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothQrScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

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

    // 관심 부스 목록 등 여러 부스를 한 화면에 보여줄 때, 부스마다 개별 COUNT를 날리는 대신
    // IN + GROUP BY로 한 번에 집계한다.
    @Query("SELECT bqs.boothId, COUNT(bqs) FROM BoothQrScan bqs " +
            "WHERE bqs.boothId IN :boothIds " +
            "AND bqs.scannedAt >= :since " +
            "AND bqs.duplicate = false " +
            "GROUP BY bqs.boothId")
    List<Object[]> countByBoothIdInAndScannedAtAfter(
            @Param("boothIds") Set<Long> boothIds,
            @Param("since") OffsetDateTime since
    );

    // WBS-157, 162: 행사 전체 부스 혼잡도 (인기 부스 상위 N개)
    // ⭐ INNER JOIN (스캔이 있는 부스만)
    @Query(
            value = "SELECT bqs.boothId, COUNT(bqs) as congestionCount " +
                    "FROM BoothQrScan bqs " +
                    "JOIN Booth b ON bqs.boothId = b.id " +
                    "WHERE b.eventId = :eventId " +
                    "AND bqs.scannedAt >= :since " +
                    "AND bqs.duplicate = false " +
                    "GROUP BY bqs.boothId " +
                    "ORDER BY congestionCount DESC",
            countQuery = "SELECT COUNT(DISTINCT bqs.boothId) " +
                    "FROM BoothQrScan bqs " +
                    "JOIN Booth b ON bqs.boothId = b.id " +
                    "WHERE b.eventId = :eventId " +
                    "AND bqs.scannedAt >= :since " +
                    "AND bqs.duplicate = false"
    )
    Page<Object[]> findPopularBooths(
            @Param("eventId") Long eventId,
            @Param("since") OffsetDateTime since,
            Pageable pageable
    );

    // WBS-163: 부스 추천 (모든 부스 포함)
    // ⭐ LEFT JOIN (스캔이 0개인 부스도 포함)
    @Query(
            value = "SELECT b.id, COALESCE(COUNT(bqs), 0) as congestionCount " +
                    "FROM Booth b " +
                    "LEFT JOIN BoothQrScan bqs ON b.id = bqs.boothId " +
                    "  AND bqs.scannedAt >= :since " +
                    "  AND bqs.duplicate = false " +
                    "WHERE b.eventId = :eventId " +
                    "GROUP BY b.id " +
                    "ORDER BY congestionCount ASC",
            countQuery = "SELECT COUNT(DISTINCT b.id) FROM Booth b WHERE b.eventId = :eventId"
    )
    Page<Object[]> findAllBoothsWithCongestion(
            @Param("eventId") Long eventId,
            @Param("since") OffsetDateTime since,
            Pageable pageable
    );

    // WBS-163: 추천 부스 조회 (혼잡 부수 제외, DB에서 필터링)
    // ⭐ LEFT JOIN + NOT IN (DB에서 excludedIds 제외)
    // 효과: in-memory 필터링 제거, 불필요한 데이터 전송 감소
    @Query(
            value = "SELECT b.id, COALESCE(COUNT(bqs), 0) as congestionCount " +
                    "FROM Booth b " +
                    "LEFT JOIN BoothQrScan bqs ON b.id = bqs.boothId " +
                    "  AND bqs.scannedAt >= :since " +
                    "  AND bqs.duplicate = false " +
                    "WHERE b.eventId = :eventId " +
                    "  AND b.id NOT IN (:excludedIds) " +
                    "GROUP BY b.id " +
                    "ORDER BY congestionCount ASC",
            countQuery = "SELECT COUNT(DISTINCT b.id) FROM Booth b " +
                    "WHERE b.eventId = :eventId AND b.id NOT IN (:excludedIds)"
    )
    Page<Object[]> findRecommendedBooths(
            @Param("eventId") Long eventId,
            @Param("since") OffsetDateTime since,
            @Param("excludedIds") Set<Long> excludedIds,
            Pageable pageable
    );

    // WBS-155: 중복 스캔 판별 (같은 부스에서의 최근 스캔 확인)
    boolean existsByBoothIdAndExchangeCodeId(Long boothId, Long exchangeCodeId);
}