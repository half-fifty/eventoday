ALTER TABLE payment_refunds
    ADD CONSTRAINT uk_payment_refunds_payment UNIQUE (payment_id);
