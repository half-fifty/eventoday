package com.min.edu.booth.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothStatus;

public interface BoothRepository extends JpaRepository<Booth, Long> {

    Optional<Booth> findByIdAndEventId(Long id, Long eventId);

    boolean existsByEventIdAndBoothCode(Long eventId, String boothCode);

    boolean existsByQrToken(String qrToken);

    @Query("SELECT b FROM Booth b WHERE b.eventId = :eventId "
        + "AND (:status IS NULL OR b.status = :status) "
        + "AND (:floorName IS NULL OR b.floorName = :floorName) "
        + "AND (:zoneName IS NULL OR b.zoneName = :zoneName) "
        + "AND (:keyword IS NULL OR "
        + "     LOWER(b.boothCode) LIKE :keyword OR "
        + "     LOWER(b.displayName) LIKE :keyword OR "
        + "     EXISTS (SELECT 1 FROM Organization o "
        + "             WHERE o.id = b.assignedOrganizationId AND LOWER(o.name) LIKE :keyword)) "
        + "ORDER BY b.id ASC")
    Page<Booth> search(
        @Param("eventId") Long eventId,
        @Param("status") BoothStatus status,
        @Param("floorName") String floorName,
        @Param("zoneName") String zoneName,
        @Param("keyword") String keyword,
        Pageable pageable
    );
}
