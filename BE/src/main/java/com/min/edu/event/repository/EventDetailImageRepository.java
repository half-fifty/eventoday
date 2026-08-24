package com.min.edu.event.repository;

import com.min.edu.event.domain.EventDetailImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventDetailImageRepository extends JpaRepository<EventDetailImage, Long> {
    List<EventDetailImage> findAllByEventIdOrderByDisplayOrderAsc(Long eventId);
    void deleteAllByEventId(Long eventId);
}
