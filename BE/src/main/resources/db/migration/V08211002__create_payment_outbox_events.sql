CREATE TABLE payment_outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    CONSTRAINT uk_payment_outbox_event_business UNIQUE (event_type, aggregate_id)
);

CREATE INDEX idx_payment_outbox_pending_available
    ON payment_outbox_events (available_at, id)
    WHERE status = 'PENDING';

CREATE INDEX idx_payment_outbox_processing_lease
    ON payment_outbox_events (lease_until, id)
    WHERE status = 'PROCESSING';
