DO $$
BEGIN
    IF EXISTS (
        SELECT payment_id
        FROM payment_refunds
        GROUP BY payment_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot add unique constraint: duplicate payment_id exists in payment_refunds';
    END IF;
END
$$;

ALTER TABLE payment_refunds
    ADD CONSTRAINT uk_payment_refunds_payment UNIQUE (payment_id);
