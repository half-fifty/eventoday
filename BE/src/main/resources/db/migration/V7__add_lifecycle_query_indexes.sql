CREATE INDEX IF NOT EXISTS idx_events_status_end_at
    ON events (status, end_at);

CREATE INDEX IF NOT EXISTS idx_advertisements_status_start_at
    ON advertisements (status, start_at);

CREATE INDEX IF NOT EXISTS idx_advertisements_status_end_at
    ON advertisements (status, end_at);
