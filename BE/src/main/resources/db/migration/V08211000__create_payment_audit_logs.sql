CREATE TABLE payment_audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_order_id BIGINT NOT NULL,
    payment_id BIGINT,
    refund_id BIGINT,
    event_type VARCHAR(60) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    source VARCHAR(30) NOT NULL,
    reason_code VARCHAR(80),
    actor_type VARCHAR(30),
    actor_id BIGINT,
    request_id VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payment_audit_logs_payment_order
        FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    CONSTRAINT fk_payment_audit_logs_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_payment_audit_logs_refund
        FOREIGN KEY (refund_id) REFERENCES payment_refunds (id)
);

CREATE INDEX idx_payment_audit_logs_payment_order_occurred
    ON payment_audit_logs (payment_order_id, occurred_at DESC, id DESC);

CREATE INDEX idx_payment_audit_logs_payment_occurred
    ON payment_audit_logs (payment_id, occurred_at DESC, id DESC)
    WHERE payment_id IS NOT NULL;

CREATE INDEX idx_payment_audit_logs_refund_occurred
    ON payment_audit_logs (refund_id, occurred_at DESC, id DESC)
    WHERE refund_id IS NOT NULL;

CREATE OR REPLACE FUNCTION prevent_payment_audit_logs_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'payment_audit_logs is append-only; % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER trg_payment_audit_logs_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON payment_audit_logs
    FOR EACH STATEMENT
    EXECUTE FUNCTION prevent_payment_audit_logs_mutation();
