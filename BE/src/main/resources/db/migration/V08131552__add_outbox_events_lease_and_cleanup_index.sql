-- 여러 인스턴스가 같은 PENDING 행을 동시에 발행하지 못하도록 PROCESSING 선점(lease)을 위한 컬럼
ALTER TABLE outbox_events ADD COLUMN lease_expires_at TIMESTAMPTZ;

CREATE INDEX idx_outbox_events_status_lease
    ON outbox_events (status, lease_expires_at);

-- 정리(cleanup) 쿼리가 status/published_at으로 조회하므로 전용 인덱스 추가
CREATE INDEX idx_outbox_events_published_at
    ON outbox_events (published_at)
    WHERE status = 'PUBLISHED';
