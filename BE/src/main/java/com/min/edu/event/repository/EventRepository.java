package com.min.edu.event.repository;

import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
    boolean existsByIdAndOrganizerOrganizationId(Long id, Long organizerOrganizationId);
    boolean existsByIdAndStatus(Long id, EventStatus status);
    @Query("SELECT e.id FROM Event e WHERE e.status = :status")
    List<Long> findIdsByStatus(@Param("status") EventStatus status);

    // 퍼널 재구성 배치용: 아직 공개 중인 행사뿐 아니라, 대상 날짜 이후에 종료된 행사도 포함한다.
    // EventLifecycleScheduler가 endAt이 지난 행사를 매분 ENDED로 바꾸므로, status만 보면
    // 행사 종료일의 방문 데이터가 재구성 대상에서 영구히 빠진다 (technical-design.md 참고).
    @Query("SELECT e.id FROM Event e WHERE e.status = :published "
            + "OR (e.status = :ended AND e.endAt >= :dayStart)")
    List<Long> findIdsForFunnelReconstruction(
            @Param("published") EventStatus published,
            @Param("ended") EventStatus ended,
            @Param("dayStart") OffsetDateTime dayStart);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Event e set e.status = :ended, e.updatedAt = :now "
            + "where e.status in :statuses and e.endAt <= :now")
    int endExpired(@Param("statuses") Collection<EventStatus> statuses,
            @Param("ended") EventStatus ended, @Param("now") OffsetDateTime now);
}
