create table ticket_order_idempotency_requests (
    id bigserial primary key,
    idempotency_key varchar(255) not null,
    request_hash varchar(64) not null,
    status varchar(30) not null,
    event_id bigint not null,
    payment_order_id bigint,
    ticket_order_id bigint,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz,
    expires_at timestamptz
);

alter table ticket_order_idempotency_requests
    add constraint uk_ticket_order_idempotency_key unique (idempotency_key);

create index idx_ticket_order_idempotency_event_id
    on ticket_order_idempotency_requests (event_id);

create index idx_ticket_order_idempotency_status_expires_at
    on ticket_order_idempotency_requests (status, expires_at);
