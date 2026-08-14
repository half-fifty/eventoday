ALTER TABLE payment_orders
    ADD COLUMN requested_payment_method VARCHAR(30) NOT NULL DEFAULT 'CARD';

CREATE TABLE payment_virtual_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id BIGINT NOT NULL UNIQUE,
    bank_code VARCHAR(10),
    account_number VARCHAR(30) NOT NULL,
    customer_name VARCHAR(100),
    due_at TIMESTAMPTZ NOT NULL,
    webhook_secret_hash VARCHAR(64) NOT NULL,
    toss_status VARCHAR(30) NOT NULL,
    deposited_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payment_virtual_accounts_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_payment_virtual_accounts_due_at
    ON payment_virtual_accounts (due_at);
