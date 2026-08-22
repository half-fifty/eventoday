package com.min.edu.funnel.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.funnel.domain.FunnelSession;

public interface FunnelSessionRepository extends JpaRepository<FunnelSession, Long> {

    Optional<FunnelSession> findBySessionId(String sessionId);

    List<FunnelSession> findByEventIdAndStartedAtBetween(Long eventId, OffsetDateTime start, OffsetDateTime end);

    List<FunnelSession> findByStartedAtBetween(OffsetDateTime start, OffsetDateTime end);
}
