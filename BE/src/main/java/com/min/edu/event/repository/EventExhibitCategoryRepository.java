package com.min.edu.event.repository;

import com.min.edu.event.domain.EventExhibitCategory;
import com.min.edu.event.domain.EventExhibitCategoryId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventExhibitCategoryRepository extends JpaRepository<EventExhibitCategory, EventExhibitCategoryId> {
    interface EventCategoryCodeRow {
        Long getEventId();
        String getCode();
    }

    void deleteAllByIdEventId(Long eventId);
    @Query("select c.code from EventExhibitCategory ec join ExhibitCategory c on c.id = ec.id.categoryId where ec.id.eventId = :eventId order by c.displayOrder")
    List<String> findCodesByEventId(@Param("eventId") Long eventId);
    @Query("select ec.id.eventId as eventId, c.code as code from EventExhibitCategory ec "
            + "join ExhibitCategory c on c.id = ec.id.categoryId "
            + "where ec.id.eventId in :eventIds and c.active = true order by c.displayOrder")
    List<EventCategoryCodeRow> findCodesByEventIds(@Param("eventIds") Collection<Long> eventIds);
}
