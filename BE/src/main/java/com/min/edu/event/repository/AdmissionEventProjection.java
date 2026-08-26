package com.min.edu.event.repository;

import com.min.edu.event.domain.EventRole;
import java.time.OffsetDateTime;

public interface AdmissionEventProjection {
    Long getEventId();
    String getEventName();
    OffsetDateTime getStartAt();
    OffsetDateTime getEndAt();
    EventRole getRole();
}
