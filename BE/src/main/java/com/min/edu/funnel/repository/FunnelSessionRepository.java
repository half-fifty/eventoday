package com.min.edu.funnel.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.funnel.domain.FunnelSession;

public interface FunnelSessionRepository extends JpaRepository<FunnelSession, Long> {

    Optional<FunnelSession> findBySessionId(String sessionId);

    // Between은 양끝을 포함해서, 정각 자정에 시작한 세션이 인접한 두 날짜 조회에 모두 걸릴 수
    // 있다. 시작 시각 <= x < 다음날 시작 시각 형태의 반열림 구간으로 하루를 명확히 나눈다.
    List<FunnelSession> findByEventIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long eventId, OffsetDateTime dayStart, OffsetDateTime dayEnd);

    List<FunnelSession> findByStartedAtGreaterThanEqualAndStartedAtLessThan(
            OffsetDateTime dayStart, OffsetDateTime dayEnd);
}
