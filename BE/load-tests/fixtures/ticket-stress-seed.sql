-- Eventoday ticket-order load-test fixture.
-- Run only against a local/test-load PostgreSQL database.
-- Re-running this script resets the load-test events and removes dependent
-- ticket-order/payment rows created for those events.

BEGIN;

WITH target_events AS (
    SELECT id
    FROM events
    WHERE name IN (
        'LOADTEST_CORRECTNESS_EVENT',
        'LOADTEST_HOTSPOT_EVENT',
        'LOADTEST_DISTRIBUTED_EVENT_01',
        'LOADTEST_DISTRIBUTED_EVENT_02',
        'LOADTEST_DISTRIBUTED_EVENT_03',
        'LOADTEST_DISTRIBUTED_EVENT_04',
        'LOADTEST_DISTRIBUTED_EVENT_05',
        'LOADTEST_DISTRIBUTED_EVENT_06',
        'LOADTEST_DISTRIBUTED_EVENT_07',
        'LOADTEST_DISTRIBUTED_EVENT_08',
        'LOADTEST_DISTRIBUTED_EVENT_09',
        'LOADTEST_DISTRIBUTED_EVENT_10'
    )
),
target_ticket_orders AS (
    SELECT payment_order_id
    FROM ticket_orders
    WHERE event_id IN (SELECT id FROM target_events)
),
deleted_virtual_accounts AS (
    DELETE FROM payment_virtual_accounts
    WHERE payment_id IN (
        SELECT id
        FROM payments
        WHERE payment_order_id IN (SELECT payment_order_id FROM target_ticket_orders)
    )
),
deleted_exchange_codes AS (
    DELETE FROM exchange_codes
    WHERE ticket_order_id IN (
        SELECT id
        FROM ticket_orders
        WHERE event_id IN (SELECT id FROM target_events)
    )
),
deleted_payments AS (
    DELETE FROM payments
    WHERE payment_order_id IN (SELECT payment_order_id FROM target_ticket_orders)
),
deleted_ticket_orders AS (
    DELETE FROM ticket_orders
    WHERE event_id IN (SELECT id FROM target_events)
)
DELETE FROM payment_orders
WHERE id IN (SELECT payment_order_id FROM target_ticket_orders);

DELETE FROM events
WHERE name IN (
    'LOADTEST_CORRECTNESS_EVENT',
    'LOADTEST_HOTSPOT_EVENT',
    'LOADTEST_DISTRIBUTED_EVENT_01',
    'LOADTEST_DISTRIBUTED_EVENT_02',
    'LOADTEST_DISTRIBUTED_EVENT_03',
    'LOADTEST_DISTRIBUTED_EVENT_04',
    'LOADTEST_DISTRIBUTED_EVENT_05',
    'LOADTEST_DISTRIBUTED_EVENT_06',
    'LOADTEST_DISTRIBUTED_EVENT_07',
    'LOADTEST_DISTRIBUTED_EVENT_08',
    'LOADTEST_DISTRIBUTED_EVENT_09',
    'LOADTEST_DISTRIBUTED_EVENT_10'
);

INSERT INTO organizations (
    organization_type,
    name,
    business_number,
    representative_name,
    contact_email,
    contact_phone,
    status,
    created_at,
    updated_at
) VALUES (
    'COMPANY',
    'LOADTEST_ORGANIZATION',
    'LOADTEST-ORG-001',
    'Load Test',
    'load-test-org@example.local',
    '01012345678',
    'ACTIVE',
    now(),
    now()
)
ON CONFLICT (business_number) DO UPDATE
SET updated_at = EXCLUDED.updated_at;

WITH loadtest_org AS (
    SELECT id
    FROM organizations
    WHERE business_number = 'LOADTEST-ORG-001'
)
INSERT INTO events (
    organizer_organization_id,
    name,
    event_type,
    short_description,
    description,
    venue_name,
    address,
    contact_email,
    contact_phone,
    postal_code,
    address_detail,
    latitude,
    longitude,
    kakao_place_id,
    region_code,
    start_at,
    end_at,
    ticket_sales_start_at,
    ticket_sales_end_at,
    ticket_price,
    ticket_total_quantity,
    ticket_sold_quantity,
    ticket_purchase_limit,
    representative_file_id,
    status,
    booth_recruitment_enabled,
    venue_map_enabled,
    booth_reservation_enabled,
    no_show_grace_minutes,
    rejection_reason,
    published_at,
    created_at,
    updated_at
)
SELECT
    loadtest_org.id,
    fixture.name,
    'EXPO',
    'Load-test event',
    'Load-test event for ticket-order baseline.',
    'Load Test Venue',
    'Seoul Load Test Address',
    'load-test-event@example.local',
    '01012345678',
    '00000',
    'Load Test Hall',
    NULL,
    NULL,
    NULL,
    'SEOUL',
    now() + interval '1 day',
    now() + interval '30 days',
    now() - interval '1 day',
    now() + interval '29 days',
    10000,
    fixture.total_quantity,
    0,
    1,
    NULL,
    'PUBLISHED',
    false,
    false,
    false,
    0,
    NULL,
    now(),
    now(),
    now()
FROM loadtest_org
CROSS JOIN (
    VALUES
        ('LOADTEST_CORRECTNESS_EVENT', 100),
        ('LOADTEST_HOTSPOT_EVENT', 100000),
        ('LOADTEST_DISTRIBUTED_EVENT_01', 100000),
        ('LOADTEST_DISTRIBUTED_EVENT_02', 100000),
        ('LOADTEST_DISTRIBUTED_EVENT_03', 100000),
        ('LOADTEST_DISTRIBUTED_EVENT_04', 100000),
        ('LOADTEST_DISTRIBUTED_EVENT_05', 100000),
        ('LOADTEST_DISTRIBUTED_EVENT_06', 100000),
        ('LOADTEST_DISTRIBUTED_EVENT_07', 100000),
        ('LOADTEST_DISTRIBUTED_EVENT_08', 100000),
        ('LOADTEST_DISTRIBUTED_EVENT_09', 100000),
        ('LOADTEST_DISTRIBUTED_EVENT_10', 100000)
) AS fixture(name, total_quantity);

COMMIT;

SELECT id, name, ticket_total_quantity, ticket_sold_quantity, status
FROM events
WHERE name LIKE 'LOADTEST_%'
ORDER BY name;
