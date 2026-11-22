# Reporting Service: Build Brief (SwiftDrop, Member 10)

Put this file in the project root. Tell the AI agent at the start of every session:
"Read Reporting_Service_Build_Brief.md and follow it exactly. Do not invent event names, tables or endpoints. If something is missing, ask me."

---

## 1. What this service is

A Spring Boot 3 microservice called `reporting-service`.
- Listens to events from RabbitMQ and keeps its own read model in its own PostgreSQL database `reporting_db`.
- Answers report queries over REST (read only).
- It must NEVER call or read the Parcel, Courier or Dispatch databases or services while handling an event.

Stack: Java 17+, Spring Boot 3, Spring Web, Spring Data JPA, Spring AMQP (RabbitMQ), PostgreSQL, Flyway, Actuator + Micrometer (Prometheus), JUnit 5, Mockito, Testcontainers (PostgreSQL, RabbitMQ), JaCoCo.
Package: `com.swiftdrop.reporting`. Layers: controller, service, repository (same as the other services).

## 2. Event contract  (CONFIRM WITH SANDAKELUM (M9) BEFORE RELYING ON THIS)

Exchange: `swiftdrop.events` (topic). Queue: `reporting.q` (durable, manual ack, prefetch 10).
Dead letter exchange: `swiftdrop.dlx`, dead letter queue: `reporting.dlq`.
Bindings: `parcel.#` and `courier.availability.changed` (drop the second one if the team does not publish it).

Assumed envelope (replace with M9's real one):
```json
{
  "eventId": "uuid",
  "eventType": "parcel.status.delivered",
  "occurredAt": "2026-11-05T09:40:00Z",
  "correlationId": "uuid",
  "payload": { "parcelId": "uuid", "...": "ids only, no phone numbers or addresses" }
}
```
Payload fields needed per event:
- parcel.created: parcelId, trackingNo, merchantId (nullable), pickupZoneId, dropoffZoneId, fee
- parcel.assigned / parcel.reassigned: parcelId, courierId (and previousCourierId for reassigned)
- parcel.status.*: parcelId (courierId if present)
- courier.availability.changed: courierId, available, activeDeliveries

## 3. How to handle each event (all in ONE database transaction)

Steps for every message:
1. Validate envelope (eventId, eventType, occurredAt present, JSON valid). If invalid: reject WITHOUT requeue (goes to `reporting.dlq`). Do not retry forever.
2. Start transaction.
3. `INSERT INTO processed_event (event_id, consumer) VALUES (?, 'reporting') ON CONFLICT DO NOTHING`. If 0 rows inserted: it is a duplicate. Commit, ack, stop.
4. Update `parcel_fact` (status only changes if the new occurredAt is later than last_event_at).
5. UPSERT the counters listed below.
6. Commit, then `basic.ack`.
If the transaction fails: do not ack (or nack to retry). A status event whose parcel_fact row does not exist yet: retry after a delay, max 3 times, then dead-letter.

The "day" of an event = occurredAt converted to timezone `Asia/Colombo` (property `reporting.timezone`).

| Event | Effect |
|---|---|
| parcel.created | insert parcel_fact. created +1 in daily, zone (dropoff zone) and merchant tables. Add fee to zone fee_total |
| parcel.assigned | set courier_id on parcel_fact. courier assigned_count +1 |
| parcel.reassigned | change courier. old courier reassigned_away +1, new courier assigned_count +1 |
| parcel.status.picked_up, in_transit | update parcel_fact status only |
| parcel.status.out_for_delivery | store ofd_at (latest). If delivered_at already known, compute duration now |
| parcel.status.delivered | delivered +1 (daily, zone, merchant, courier). Store delivered_at. If ofd_at known: courier total_minutes += minutes, timed_count +1 |
| parcel.status.failed | failed +1 (daily, zone, merchant). courier failed_attempts +1 |
| parcel.status.rescheduled | rescheduled +1 (daily) |
| parcel.status.returned | returned +1 (daily) |
| parcel.cancelled | cancelled +1 (daily) |
| courier.availability.changed | replace row in courier_state (not a counter) |

Counters must be incremented with an atomic UPSERT, for example:
```sql
INSERT INTO daily_delivery_stats (stat_date, delivered_count) VALUES (:day, 1)
ON CONFLICT (stat_date) DO UPDATE SET delivered_count = daily_delivery_stats.delivered_count + 1;
```
Counters only ever go +1 per unique event, so order of arrival does not change them.
Delivery duration is computed when both ofd_at and delivered_at are known, whichever event arrives last.

## 4. Database (Flyway migration V1__init.sql)

```sql
CREATE TABLE processed_event (
  event_id UUID NOT NULL,
  consumer VARCHAR(50) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, consumer)
);

CREATE TABLE parcel_fact (
  parcel_id UUID PRIMARY KEY,
  tracking_no VARCHAR(40),
  merchant_id UUID,
  courier_id UUID,
  pickup_zone_id UUID,
  dropoff_zone_id UUID,
  fee NUMERIC(12,2),
  current_status VARCHAR(30) NOT NULL,
  last_event_at TIMESTAMPTZ NOT NULL,
  ofd_at TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ
);

CREATE TABLE daily_delivery_stats (
  stat_date DATE PRIMARY KEY,
  created_count INT NOT NULL DEFAULT 0,
  delivered_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  rescheduled_count INT NOT NULL DEFAULT 0,
  returned_count INT NOT NULL DEFAULT 0,
  cancelled_count INT NOT NULL DEFAULT 0
);

CREATE TABLE courier_daily_stats (
  stat_date DATE NOT NULL,
  courier_id UUID NOT NULL,
  assigned_count INT NOT NULL DEFAULT 0,
  reassigned_away_count INT NOT NULL DEFAULT 0,
  delivered_count INT NOT NULL DEFAULT 0,
  failed_attempts INT NOT NULL DEFAULT 0,
  total_minutes BIGINT NOT NULL DEFAULT 0,
  timed_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (stat_date, courier_id)
);

CREATE TABLE zone_daily_stats (
  stat_date DATE NOT NULL,
  zone_id UUID NOT NULL,
  created_count INT NOT NULL DEFAULT 0,
  delivered_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  fee_total NUMERIC(14,2) NOT NULL DEFAULT 0,
  PRIMARY KEY (stat_date, zone_id)
);

CREATE TABLE merchant_daily_stats (
  stat_date DATE NOT NULL,
  merchant_id UUID NOT NULL,
  created_count INT NOT NULL DEFAULT 0,
  delivered_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (stat_date, merchant_id)
);

CREATE TABLE courier_state (
  courier_id UUID PRIMARY KEY,
  available BOOLEAN NOT NULL,
  active_deliveries INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL
);
```
Average courier time = total_minutes / timed_count (not available if timed_count = 0).

## 5. REST API (read only, under /api/v1, reached through the API Gateway)

| Endpoint | Roles | Returns |
|---|---|---|
| GET /api/v1/admin/reports/summary?from&to | ADMIN, DISPATCHER | created, delivered, failed, attempt rate, final-outcome rate, active couriers, lastUpdatedAt |
| GET /api/v1/admin/reports/deliveries-per-day?from&to&zoneId | ADMIN, DISPATCHER | one point per day |
| GET /api/v1/admin/reports/success-rate?from&to | ADMIN, DISPATCHER | both rates |
| GET /api/v1/admin/reports/courier-performance?from&to&sort | ADMIN, DISPATCHER | row per courier |
| GET /api/v1/admin/reports/zones?from&to | ADMIN, DISPATCHER | row per zone |
| GET /api/v1/merchant/reports/orders?from&to | MERCHANT | only the caller's own merchant id (from the token, NEVER from the query string) |

Rules:
- Attempt rate (one day) = delivered / (delivered + failed attempts).
- Final-outcome rate (date range) = delivered / (delivered + returned).
- Return rate as a number or null if the divisor is 0.
- `from` must not be after `to`, and the range must be at most 366 days, otherwise HTTP 400 in the team's shared error format.
- `format=csv` returns CSV. Escape any cell starting with `=`, `+`, `-` or `@` by prefixing an apostrophe.
- Role check: no token 401, wrong role 403. Read the role from the JWT claims the gateway forwards. Check the role again inside this service.
- Include `lastUpdatedAt` (time of last processed event) in the summary.
- Courier, zone and merchant names are looked up by REST from their services and cached for 10 minutes (stub these for now; return ids if the lookup is not available).

## 6. Other requirements

- Actuator health and /actuator/prometheus. Logs must carry the correlation id. Never log phone numbers, addresses or tokens.
- Config through environment variables (DB url, RabbitMQ host, timezone). No secrets in the repository.
- Nightly reconciliation job (@Scheduled): compare yesterday's delivered/failed counts with the Parcel service and log a warning plus set a flag shown in the summary if they differ. Stub the Parcel call behind an interface.
- Unit test coverage at least 70% (JaCoCo). Fail the build below that.

## 7. Test data to reproduce (expected results are worked out by hand)

5 parcels, day D and D+1, 31 events in total:
- P1: merchant M1, zone Z2, courier C1. out_for_delivery 09:00, delivered 09:40 on D (40 min).
- P2: M1, Z2, C1. out_for_delivery 09:00, delivered 10:20 on D (80 min).
- P3: M1, Z2, C2. out_for_delivery 11:00, failed 11:30, returned 16:00 on D.
- P4: other customer, Z2, C1. failed 13:40 on D, rescheduled. out_for_delivery 09:30, delivered 10:00 on D+1 (30 min).
- P5: Z2, C2. created, assigned, cancelled on D.
Every parcel also has created, assigned, picked_up and in_transit events (except P5).

Expected:
- Company, day D: created 5, delivered 2, failed 2, returned 1, rescheduled 1, cancelled 1.
- Company, day D+1: delivered 1, failed 0.
- Attempt rate day D = 2/(2+2) = 50.0%. Final-outcome rate D to D+1 = 3/(3+1) = 75.0%.
- Courier C1 day D: assigned 3, delivered 2, failed attempts 1, average 60.0 min. C1 day D+1: delivered 1, average 30.0 min.
- Courier C2 day D: assigned 2, delivered 0, failed attempts 1, average not available.
- Zone Z2 day D: created 5, delivered 2, failed 2.
- Merchant M1 day D: created 3, delivered 2, failed 1.

Tests to write:
- T1 accuracy (all numbers above)
- T2 duplicates (publish all events twice, totals unchanged, 31 rows in processed_event)
- T3 out of order (P1 delivered before out_for_delivery, same counters, 40 min appears once the second event arrives)
- T4 shuffled order, 20 runs with different random seeds
- T5 poison message (missing eventId) lands in reporting.dlq and the queue keeps working
- T6 role checks on every endpoint
- T7 date validation (from after to, range over 366 days) returns 400
- T8 CSV formula escaping
- T9 reconciliation flags a mismatch

---

# Prompts to paste into Antigravity (one phase at a time)

Always start with: "Read Reporting_Service_Build_Brief.md and follow it exactly. Do not invent event names, tables or endpoints."

**Phase 1:** "Create the Spring Boot 3 project `reporting-service` (Maven), package com.swiftdrop.reporting, with Web, Data JPA, AMQP, PostgreSQL, Flyway, Actuator, Micrometer Prometheus, Testcontainers and JaCoCo. Add a Dockerfile and application.yml reading config from environment variables. Add Flyway migration V1__init.sql exactly as in section 4 of the brief. Add JPA entities and repositories. Show me the file list when done."

**Phase 2:** "Implement the RabbitMQ setup (exchange, reporting.q, reporting.dlq with dead-letter exchange, bindings) and the event consumer exactly as in sections 2 and 3. One database transaction per message, guest-list check with ON CONFLICT DO NOTHING, atomic UPSERT counters, manual ack, and reject-without-requeue for invalid envelopes. Put the handling logic in a service class with one method per event type so it is easy to unit test."

**Phase 3:** "Implement the REST endpoints in section 5 with role checks, date validation, the two success-rate formulas, CSV export with formula escaping, and the shared error format. Stub name lookups behind an interface."

**Phase 4:** "Write the tests in section 7 (T1 to T9) using Testcontainers for PostgreSQL and RabbitMQ, using the exact fixture and expected numbers. Add JaCoCo with a 70% line coverage check that fails the build. Run the tests and show me the results and the coverage."

**Phase 5:** "Add a docker-compose service for reporting-service and reporting_db, a GitHub Actions job that builds, runs the tests and uploads the JaCoCo report as an artifact."

**Phase 6 (admin pages):** "In the Angular admin console, add three routes behind a role guard: /admin/users (ADMIN only), /admin/merchants (ADMIN only), /admin/reports (ADMIN and DISPATCHER). Use mock services for now. The reports page needs a filter bar (today, 7 days, 30 days, custom range, zone), KPI cards with a 'data updated at' time, a line chart of created and delivered per day, a stacked bar of delivered and failed, a sortable courier performance table, a zone bar chart, Export CSV, loading and error states, and 60 second auto refresh. Rejecting a merchant needs a reason of at least 10 characters. An admin cannot change their own role or lock themselves."

## Tips for using the AI agent safely

1. One phase at a time. Read the code it writes before moving on.
2. If it changes an event name, table or endpoint that is in this brief, tell it to put it back. Change the brief first if the team agrees on something different.
3. Ask it to run the tests itself and show the output, not just say they pass.
4. Commit to Git after every phase so you can go back.
5. Never paste passwords, tokens or API keys into the chat.
