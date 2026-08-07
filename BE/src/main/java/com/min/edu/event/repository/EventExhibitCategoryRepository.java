package com.min.edu.event.repository;

import com.min.edu.event.domain.EventExhibitCategory;
import com.min.edu.event.domain.EventExhibitCategoryId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventExhibitCategoryRepository extends JpaRepository<EventExhibitCategory, EventExhibitCategoryId> {
    void deleteAllByIdEventId(Long eventId);
    @Query("select c.code from EventExhibitCategory ec join ExhibitCategory c on c.id = ec.id.categoryId where ec.id.eventId = :eventId order by c.displayOrder")
    List<String> findCodesByEventId(@Param("eventId") Long eventId);
    @Query("select distinct ec.id.eventId from EventExhibitCategory ec join ExhibitCategory c on c.id = ec.id.categoryId where c.code in :codes")
    List<Long> findEventIdsByCategoryCodes(@Param("codes") Collection<String> codes);
}
