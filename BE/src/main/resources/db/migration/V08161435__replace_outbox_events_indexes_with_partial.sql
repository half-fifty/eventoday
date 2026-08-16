-- findClaimableCandidates()는 (PENDING and next_attempt_at<=now) or (PROCESSING and lease_expires_at<now)로
-- ORDER BY id LIMIT 50을 조회한다. 기존 (status, next_attempt_at)/(status, lease_expires_at) 복합
-- 인덱스로는, 대기 중인 행 대부분이 아직 재시도 시각이 안 된 상황(backoff 몰릴 때)에서 플래너가
-- 통계 추정을 잘못해 PK 순서 전체 스캔으로 빠지는 것을 EXPLAIN ANALYZE로 확인했다(20만 건 기준
-- 43ms/버퍼 83,992). 각 분기 전용 부분 인덱스로 교체하면 같은 조건에서 0.15ms/버퍼 163으로
-- 줄어들고, 플래너가 항상 BitmapOr 계획을 안정적으로 선택한다.
DROP INDEX IF EXISTS idx_outbox_events_status_next_attempt;
DROP INDEX IF EXISTS idx_outbox_events_status_lease;

CREATE INDEX idx_outbox_events_pending_next_attempt
    ON outbox_events (next_attempt_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_events_processing_lease
    ON outbox_events (lease_expires_at)
    WHERE status = 'PROCESSING';
