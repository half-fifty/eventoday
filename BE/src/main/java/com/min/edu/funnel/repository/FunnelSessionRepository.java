package com.min.edu.funnel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.funnel.domain.FunnelSession;

public interface FunnelSessionRepository extends JpaRepository<FunnelSession, Long> {

    Optional<FunnelSession> findBySessionId(String sessionId);
}
