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
    // CONTENT-API-006: 전체 공지 목록에서 공개(PUBLISHED) 행사만 대상으로 조회
    List<Event> findAllByStatus(EventStatus status);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Event e set e.status = :ended, e.updatedAt = :now "
            + "where e.status in :statuses and e.endAt <= :now")
    int endExpired(@Param("statuses") Collection<EventStatus> statuses,
            @Param("ended") EventStatus ended, @Param("now") OffsetDateTime now);
}
