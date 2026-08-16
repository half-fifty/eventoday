package com.min.edu.notification.outbox.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.min.edu.notification.outbox.domain.OutboxEvent;
import com.min.edu.notification.outbox.domain.OutboxEventStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 발행 대상 후보: 재시도 시각이 지난 PENDING 행 + lease가 만료된(=크래시 등으로
     * 방치된) PROCESSING 행. 아직 선점(claim)되지 않은 목록이라 여러 인스턴스가
     * 겹쳐서 조회할 수 있고, 실제 중복 처리 방지는 claim()의 원자적 조건부 UPDATE가 담당한다.
     */
    @Query(
        "select e from OutboxEvent e where "
            + "(e.status = com.min.edu.notification.outbox.domain.OutboxEventStatus.PENDING and e.nextAttemptAt <= :now) or "
            + "(e.status = com.min.edu.notification.outbox.domain.OutboxEventStatus.PROCESSING and e.leaseExpiresAt < :now) "
            + "order by e.id asc"
    )
    List<OutboxEvent> findClaimableCandidates(@Param("now") OffsetDateTime now, Pageable pageable);

    /**
     * 조건에 맞을 때만 PROCESSING으로 전환하는 원자적 선점. 반환값이 1이면 이 호출이
     * 선점에 성공한 것이고, 0이면 다른 스레드/인스턴스가 이미 가져갔거나 아직 재시도
     * 시각이 안 됐으므로 건너뛴다.
     *
     * <p>PENDING 분기에도 nextAttemptAt <= :now를 반드시 확인해야 한다. 스레드풀이
     * 밀려 대기하던 오래된 발행 작업이, 그 사이 다른 작업이 이 행을 실패 처리해
     * (status를 다시 PENDING으로, nextAttemptAt을 미래로) 둔 뒤에 뒤늦게 실행되면
     * — 이 조건이 없을 경우 백오프를 무시하고 즉시 재선점해 재시도해버린다.</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update OutboxEvent e set e.status = com.min.edu.notification.outbox.domain.OutboxEventStatus.PROCESSING, "
            + "e.leaseExpiresAt = :leaseExpiresAt "
            + "where e.id = :id and ("
            + "(e.status = com.min.edu.notification.outbox.domain.OutboxEventStatus.PENDING and e.nextAttemptAt <= :now) or "
            + "(e.status = com.min.edu.notification.outbox.domain.OutboxEventStatus.PROCESSING and e.leaseExpiresAt < :now))"
    )
    int claim(
        @Param("id") Long id,
        @Param("now") OffsetDateTime now,
        @Param("leaseExpiresAt") OffsetDateTime leaseExpiresAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "delete from OutboxEvent e where e.status = :status and e.publishedAt < :threshold"
    )
    int deleteByStatusAndPublishedAtBefore(
        @Param("status") OutboxEventStatus status,
        @Param("threshold") OffsetDateTime threshold
    );
}
