package com.min.edu.event.repository;

import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
    boolean existsByIdAndOrganizerOrganizationId(Long id, Long organizerOrganizationId);
    boolean existsByIdAndStatus(Long id, EventStatus status);
    List<Event> findAllByStatusInAndEndAtLessThanEqual(
            Collection<EventStatus> statuses, OffsetDateTime endAt);
}
