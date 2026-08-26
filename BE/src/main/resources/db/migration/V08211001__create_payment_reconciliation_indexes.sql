CREATE INDEX CONCURRENTLY idx_payments_payment_order_id
    ON payments (payment_order_id);

CREATE INDEX CONCURRENTLY idx_payment_orders_va_pending_created_at
    ON payment_orders (created_at, id)
    WHERE requested_payment_method = 'VIRTUAL_ACCOUNT'
        AND status = 'PENDING';
