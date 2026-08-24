package com.min.edu.booth.repository;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoothRepository extends JpaRepository<Booth, Long> {

    Optional<Booth> findByIdAndEventId(Long id, Long eventId);

    List<Booth> findByEventIdAndIdIn(Long eventId, Collection<Long> ids);



    List<Booth> findByIdIn(List<Long> ids);

    boolean existsByEventIdAndBoothCode(Long eventId, String boothCode);
    boolean existsByEventId(Long eventId);

    boolean existsByQrToken(String qrToken);

    @Query("SELECT b.boothCode FROM Booth b WHERE b.eventId = :eventId AND b.boothCode IN :boothCodes")
    List<String> findExistingBoothCodes(@Param("eventId") Long eventId, @Param("boothCodes") Collection<String> boothCodes);

    @Query("SELECT b FROM Booth b WHERE b.eventId = :eventId AND b.status IN :statuses ORDER BY b.id ASC")
    Page<Booth> searchPublic(
            @Param("eventId") Long eventId,
            @Param("statuses") Collection<BoothStatus> statuses,
            Pageable pageable
    );

    @Query("SELECT b FROM Booth b WHERE b.eventId = :eventId "
            + "AND (:status IS NULL OR b.status = :status) "
            + "AND (:floorName IS NULL OR b.floorName = :floorName) "
            + "AND (:zoneName IS NULL OR b.zoneName = :zoneName) "
            + "AND (:keyword IS NULL OR "
            + "     LOWER(b.boothCode) LIKE :keyword ESCAPE '!' OR "
            + "     LOWER(b.displayName) LIKE :keyword ESCAPE '!' OR "
            + "     EXISTS (SELECT 1 FROM Organization o "
            + "             WHERE o.id = b.assignedOrganizationId AND LOWER(o.name) LIKE :keyword ESCAPE '!')) "
            + "ORDER BY b.id ASC")
    Page<Booth> search(
            @Param("eventId") Long eventId,
            @Param("status") BoothStatus status,
            @Param("floorName") String floorName,
            @Param("zoneName") String zoneName,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /**
     * 동시 신청 충돌 방지를 위한 비관적 락 조회 (WBS-090)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booth b WHERE b.id = :id")
    Optional<Booth> findByIdWithLock(@Param("id") Long id);

    // boothCode LIKE 검색으로 해당 행사의 부스 ID 목록 조회 (APP-API-005 boothCode 필터용)
    @Query("SELECT b.id FROM Booth b "
            + "WHERE b.eventId = :eventId "
            + "AND LOWER(b.boothCode) LIKE :boothCode ESCAPE '!'")
    List<Long> findIdsByBoothCodeLike(
            @Param("eventId") Long eventId,
            @Param("boothCode") String boothCode
    );

    // ===== 모바일 안내 API (GUIDE-API-001~003) =====

    // GUIDE-API-001, 002: 행사별 부스 목록 조회


    // GUIDE-API-002: 부스 검색 (부스명 기반)
    List<Booth> findByEventIdAndDisplayNameContainingIgnoreCase(Long eventId, String displayName);

    // 행사 내 전체 부스 조회 (통계 집계용)
    List<Booth> findByEventId(Long eventId);

    /**
     * 행사 내에서 부스명으로 검색 (Pageable 지원)
     */
    Page<Booth> findByEventIdAndDisplayNameContainingIgnoreCase(
            Long eventId,
            String keyword,
            Pageable pageable);


}
