# Eventoday Ticket Order Baseline Load Tests

Do not run these scripts against production. Use only a local or test-load database. These scripts do not call Toss confirm, and they should not be mixed with real Toss API traffic. High `RATE` or `VUS` values can saturate Windows, Docker Desktop, PostgreSQL, or the Spring Boot process before the application logic is the bottleneck.

This folder contains the Redis-before baseline for the current ticket-order implementation. It targets `POST /api/events/{eventId}/ticket-orders`, where `JpaTicketInventoryGateway.reserve()` updates one `events` row with:

```sql
update events
set ticket_sold_quantity = ticket_sold_quantity + ?
where id = ?
  and ticket_sold_quantity + ? <= ticket_total_quantity
```

The goal is to measure correctness and row contention before adding any Redis-based control layer.

## Files

- `fixtures/ticket-stress-seed.sql`: local/test-load fixture for one correctness event, one hotspot event, and ten distributed-control events.
- `k6/ticket-order-correctness.js`: one burst request per VU against a 100-ticket event.
- `k6/ticket-order-load.js`: the official constant arrival-rate script for both hotspot and distributed runs. The only intended difference is the number of ids in `EVENT_IDS`.

## Prepare Local Data

Run the fixture only against a disposable local/test-load PostgreSQL database. It deletes and recreates only events named `LOADTEST_*` and their dependent ticket-order/payment rows.

PowerShell example:

```powershell
psql "postgresql://expo_user:<password>@localhost:5432/expo_platform" -f .\load-tests\fixtures\ticket-stress-seed.sql
```

After running it, copy the returned ids:

- `LOADTEST_CORRECTNESS_EVENT` -> `EVENT_ID` for correctness.
- `LOADTEST_HOTSPOT_EVENT` -> single-item `EVENT_IDS` for hotspot.
- `LOADTEST_DISTRIBUTED_EVENT_01` through `LOADTEST_DISTRIBUTED_EVENT_10` -> comma-separated `EVENT_IDS`.

The fixture creates its own organization using `business_number='LOADTEST-ORG-001'`, so existing organization/member rows are not required.

## k6 Availability

Check whether k6 is installed:

```powershell
k6 version
```

Docker-based execution avoids installing k6 on Windows:

```powershell
docker run --rm -i grafana/k6 version
```

When using Docker k6 against Spring Boot running on the Windows host, use `http://host.docker.internal:8080` as `BASE_URL`. When using native Windows k6, use `http://localhost:8080`.

## Correctness Burst

Purpose: verify no oversell when many users concurrently order the same 100-ticket event.

Native k6:

```powershell
$env:BASE_URL="http://localhost:8080"
$env:EVENT_ID="<LOADTEST_CORRECTNESS_EVENT id>"
$env:VUS="100"
k6 run .\load-tests\k6\ticket-order-correctness.js
```

Docker k6:

```powershell
docker run --rm `
  -e BASE_URL="http://host.docker.internal:8080" `
  -e EVENT_ID="<LOADTEST_CORRECTNESS_EVENT id>" `
  -e VUS="100" `
  -v "${PWD}\load-tests\k6:/scripts" `
  grafana/k6 run /scripts/ticket-order-correctness.js
```

Expected result with inventory 100:

- `order_success <= 100`
- final `events.ticket_sold_quantity <= 100`
- created `ticket_orders` for the event `<= 100`
- no oversell

If all requests reach the application and the DB remains healthy, `order_success` should be exactly 100 and the remaining responses should be `sold_out`.

## Duplicate Idempotency Burst

Purpose: verify that 100 concurrent retries of the same logical ticket-order request with
one `Idempotency-Key` create only one real order side effect. The HTTP success count may be
greater than one because completed retries can replay the existing order response.

Docker k6:

```powershell
docker run --rm `
  -e BASE_URL="http://host.docker.internal:8080" `
  -e EVENT_ID="<LOADTEST_CORRECTNESS_EVENT id or another clean paid-ticket event id>" `
  -e IDEMPOTENCY_KEY="duplicate-burst-$(Get-Date -Format yyyyMMddHHmmss)" `
  -e VUS="100" `
  -e ITERATIONS="100" `
  -e REQ_TIMEOUT="10s" `
  -v "${PWD}\load-tests\k6:/scripts" `
  grafana/k6 run /scripts/ticket-order-duplicate-burst.js
```

Expected DB result:

```sql
select count(*) as ticket_orders_for_event
from ticket_orders
where event_id = :event_id;

select id, ticket_total_quantity, ticket_sold_quantity
from events
where id = :event_id;

select count(*) as idempotency_rows
from ticket_order_idempotency_requests
where idempotency_key = :idempotency_key;

select status, count(*)
from ticket_order_idempotency_requests
where idempotency_key = :idempotency_key
group by status;

select count(*) as payment_orders_for_duplicate_burst
from payment_orders p
join ticket_orders t on t.payment_order_id = p.id
where t.event_id = :event_id
  and p.buyer_email = 'duplicate-burst@example.local';
```

For a clean fixture event and fresh `IDEMPOTENCY_KEY`, expected values are:

- `ticket_orders_for_event` increases by 1
- `events.ticket_sold_quantity` increases by 1
- `idempotency_rows = 1`
- `COMPLETED = 1`
- `payment_orders_for_duplicate_burst = 1`

## Hotspot Stress

Purpose: continuously contend on one `events` row while avoiding early sold-out.

Use the common script. For hotspot, set `EVENT_IDS` to exactly one event id.

Native k6:

```powershell
$env:BASE_URL="http://localhost:8080"
$env:EVENT_IDS="<LOADTEST_HOTSPOT_EVENT id>"
$env:RATE="50"
$env:DURATION="30s"
$env:PRE_ALLOCATED_VUS="50"
$env:MAX_VUS="200"
k6 run .\load-tests\k6\ticket-order-load.js
```

Increase cautiously after the baseline is stable:

```powershell
$env:RATE="100"   # then 200, then 300
```

Avoid 500+ req/s on a development PC unless you are explicitly testing local saturation.

After enabling ticket-order reliability controls, a 500 RPS hotspot run is expected to shed
excess concurrency quickly with `TICKET_429_001` instead of letting every request wait on the
same PostgreSQL row. Use Docker k6 with the same Before/After comparison conditions:

```powershell
docker run --rm `
  -e BASE_URL="http://host.docker.internal:8080" `
  -e EVENT_IDS="<LOADTEST_HOTSPOT_EVENT id>" `
  -e RATE="500" `
  -e DURATION="30s" `
  -e PRE_ALLOCATED_VUS="300" `
  -e MAX_VUS="1000" `
  -e REQ_TIMEOUT="10s" `
  -v "${PWD}\load-tests\k6:/scripts" `
  grafana/k6 run /scripts/ticket-order-load.js
```

The admission limit is controlled by `TICKET_ORDER_ADMISSION_MAX_IN_FLIGHT_PER_EVENT`.
For the first 500 RPS hotspot After experiment, try `8` or `10` as a tuning experiment
when the application uses the default Hikari pool size. This is not an optimal default;
adjust it from observed timeout, Hikari pending, and PostgreSQL lock-wait results.

## Distributed Control

Purpose: use the same load shape as hotspot while removing single-row concentration. The only intended variable is event id distribution.

Use the same common script. For distributed, set `EVENT_IDS` to several event ids. Keep every other environment variable identical to the hotspot run.

Native k6:

```powershell
$env:BASE_URL="http://localhost:8080"
$env:EVENT_IDS="10,11,12,13,14,15,16,17,18,19"
$env:RATE="50"
$env:DURATION="30s"
$env:PRE_ALLOCATED_VUS="50"
$env:MAX_VUS="200"
k6 run .\load-tests\k6\ticket-order-load.js
```

Use the ids from `LOADTEST_DISTRIBUTED_EVENT_01` through `LOADTEST_DISTRIBUTED_EVENT_10`. Keep `BASE_URL`, `API_PREFIX`, `RATE`, `DURATION`, `PRE_ALLOCATED_VUS`, `MAX_VUS`, `REQ_TIMEOUT`, BE process, PostgreSQL instance, and machine conditions equal to the hotspot run. Do not change request data between the two runs.

The script selects events with global scenario iteration order:

```javascript
eventIds[exec.scenario.iterationInTest % eventIds.length]
```

With one id, all requests use the same event. With ten ids, requests are assigned round-robin across the ten ids using the whole scenario's iteration count, not each VU's local `__ITER`.

## Metrics To Record

k6:

- `http_reqs`
- `http_req_duration`: avg, med, p90, p95, p99, max
- `http_req_failed`
- request rate
- `order_success`
- `sold_out`
- `business_error`
- `unexpected_error`
- `timeout_error` for hotspot/distributed
- `admission_rejected`: intentional `TICKET_429_001` load shedding/backpressure
- `idempotency_conflict`: `TICKET_409_002`, same key reused for a different request
- `idempotency_in_progress`: `TICKET_409_003`, same key retry while the original request is still processing

Spring Actuator/Micrometer, if actuator endpoints are exposed in a local profile:

- `hikaricp.connections.active`
- `hikaricp.connections.idle`
- `hikaricp.connections.pending`
- `hikaricp.connections.max`
- JVM CPU, memory, thread count
- Tomcat active request threads

Do not change production settings just to expose actuator metrics. If needed later, expose only the minimal actuator endpoints in a local load-test profile.

## PostgreSQL Observation SQL

Run these manually during a load test from a separate terminal. They are read-only.

Connection and wait overview:

```sql
select state, wait_event_type, wait_event, count(*)
from pg_stat_activity
where datname = current_database()
group by state, wait_event_type, wait_event
order by count(*) desc;
```

Active queries:

```sql
select pid, state, wait_event_type, wait_event, now() - query_start as age, left(query, 160) as query
from pg_stat_activity
where datname = current_database()
  and state <> 'idle'
order by age desc;
```

Blocked/blocking queries for PostgreSQL 17:

```sql
select
    blocked.pid as blocked_pid,
    blocker.pid as blocking_pid,
    blocked.wait_event_type,
    blocked.wait_event,
    now() - blocked.query_start as blocked_age,
    left(blocked.query, 160) as blocked_query,
    left(blocker.query, 160) as blocking_query
from pg_stat_activity blocked
join pg_stat_activity blocker
  on blocker.pid = any(pg_blocking_pids(blocked.pid))
where blocked.datname = current_database()
order by blocked_age desc;
```

Correctness verification after a run:

```sql
select id, name, ticket_total_quantity, ticket_sold_quantity,
       ticket_sold_quantity > ticket_total_quantity as oversell
from events
where id = :event_id;

select count(*) as created_ticket_orders
from ticket_orders
where event_id = :event_id;

select status, count(*)
from ticket_orders
where event_id = :event_id
group by status
order by status;
```

## Result Template

Record three runs per condition and compare the median.

| Field | Run 1 | Run 2 | Run 3 |
| --- | --- | --- | --- |
| Date | | | |
| Git commit | | | |
| PC/environment | | | |
| BE execution | | | |
| PostgreSQL execution | | | |
| Scenario | | | |
| RATE or VUS | | | |
| Duration | | | |
| Inventory | | | |
| Total requests | | | |
| Success | | | |
| Sold out | | | |
| Business error | | | |
| Unexpected failure | | | |
| Admission rejected | | | |
| Idempotency conflict | | | |
| Idempotency in progress | | | |
| TPS | | | |
| avg | | | |
| p50/med | | | |
| p95 | | | |
| p99 | | | |
| max | | | |
| Hikari active peak | | | |
| Hikari pending peak | | | |
| PostgreSQL connection peak | | | |
| Lock wait observed | | | |
| CPU peak | | | |
| Final ticket_sold_quantity | | | |
| Created TicketOrder count | | | |
| Oversell yes/no | | | |

## Interpreting Hotspot vs Distributed

Compare hotspot and distributed with the same `RATE`, `DURATION`, `PRE_ALLOCATED_VUS`, `MAX_VUS`, BE process, DB instance, and fixture inventory.

If hotspot has materially higher p95/p99, more PostgreSQL lock waits, and higher Hikari pending connections while distributed does not, the evidence points to single `events` row update contention. If both degrade similarly, the bottleneck is more likely application CPU, DB connection pool size, disk, Docker Desktop, or general insert throughput rather than the inventory row.

For the After comparison, do not treat `admission_rejected` as `unexpected_error`.
The reliability goal is:

- request timeouts drop materially
- dropped iterations drop materially
- p95/p99 stabilize
- PostgreSQL lock waiters decrease
- Hikari pending connections decrease
- admission overflow returns fast 429 responses
- oversell remains 0
- duplicate orders remain 0
- PostgreSQL remains stable under the configured admission limit
