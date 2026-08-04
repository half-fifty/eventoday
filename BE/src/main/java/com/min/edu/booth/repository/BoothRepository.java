package com.min.edu.booth.repository;

import java.util.Collection;
import java.util.List;
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
}
