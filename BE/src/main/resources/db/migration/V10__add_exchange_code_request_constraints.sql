DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM exchange_code_requests
        WHERE status NOT IN ('REQUESTED', 'APPROVED', 'REJECTED', 'ISSUED')
    ) THEN
        RAISE EXCEPTION
            'Cannot add chk_exchange_code_requests_status: invalid exchange_code_requests.status exists';
    END IF;
END
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT event_id
        FROM exchange_code_requests
        WHERE status = 'REQUESTED'
        GROUP BY event_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot create uk_exchange_code_requests_event_requested: duplicate REQUESTED rows per event_id exist';
    END IF;
END
$$;

ALTER TABLE exchange_code_requests
    ADD CONSTRAINT chk_exchange_code_requests_status
    CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'ISSUED'));

CREATE UNIQUE INDEX uk_exchange_code_requests_event_requested
    ON exchange_code_requests (event_id)
    WHERE status = 'REQUESTED';

CREATE INDEX idx_exchange_code_requests_event_status_created
    ON exchange_code_requests (event_id, status, created_at DESC, id DESC);

CREATE INDEX idx_exchange_code_requests_status_created
    ON exchange_code_requests (status, created_at DESC, id DESC);
