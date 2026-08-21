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

CREATE INDEX idx_payments_payment_order_id
    ON payments (payment_order_id);

CREATE INDEX idx_payment_orders_va_pending_created_at
    ON payment_orders (created_at, id)
    WHERE requested_payment_method = 'VIRTUAL_ACCOUNT'
        AND status = 'PENDING';
