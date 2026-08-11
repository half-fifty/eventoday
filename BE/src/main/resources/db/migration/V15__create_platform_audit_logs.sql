CREATE TABLE platform_audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_member_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL,
    action VARCHAR(40) NOT NULL,
    target_id BIGINT NOT NULL,
    target_name VARCHAR(200) NOT NULL,
    detail TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_platform_audit_logs_actor FOREIGN KEY (actor_member_id) REFERENCES members(id)
);

CREATE INDEX idx_platform_audit_logs_occurred_at
    ON platform_audit_logs (occurred_at DESC, id DESC);
