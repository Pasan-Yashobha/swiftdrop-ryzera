# Audit Log Service (Member 1)

Answers "who did what, and when?" for admin and security actions. It listens to the event backbone, keeps an
**append-only, tamper-evident** record, and gives admins a read-only API for the Audit Log page.
Spring Boot 3.5, Java 21, PostgreSQL (`audit_db`), RabbitMQ. Port **8091** on your machine (8080 inside Docker).

## How it works

```
publishers (Merchant, Identity, Dispatch, Webhook...)
   -> exchange swiftdrop.events (topic)
   -> queue audit.q  (bound to user.#, merchant.#, admin.#, parcel.assigned, webhook.delivery.#)
   -> AuditEventListener  (1 consumer thread, 3 attempts)  --failed--> swiftdrop.dlx -> audit.dlq
   -> EventMapper (event -> audit entry)  -> DetailsSanitizer (no secrets / personal data)
   -> AuditRecorder: skip duplicate event_id, chain hash to the previous record, INSERT
   -> audit_log table
   -> AuditLogController (ADMIN only): search, one record, verify chain
```

* **Generic mapping:** `merchant.approved` becomes action `MERCHANT_APPROVED`, entity type `MERCHANT`, entity id = the event's
  `aggregateId`. A new event type needs **no code change**, only a routing key that `audit.q` is bound to.
* **Who did it:** the first of `actorId`, `decidedBy`, `adminId`, `performedBy`, `changedBy`, `userId`, `ownerUserId` in the event's `data`.
  Optional `data.actorRole` and `data.ipAddress` are stored too.
* **Idempotent:** `event_id` is unique, so a redelivered event is recorded once.
* **No sensitive data:** keys containing password, secret, token, authorization, email, phone, address, hash, credential (or `key`, `apiKey`)
  are dropped; other values are stored as short text (max 200 characters, 30 entries).

## Tamper protection

1. No API can change or delete a record.
2. A database trigger blocks `UPDATE`, `DELETE` and `TRUNCATE` on `audit_log`.
3. Each record stores `row_hash = SHA-256(previous hash + its own fields)`. `GET /api/v1/admin/audit-logs/verify` re-computes the chain
   and reports the first broken record.

This is tamper-**evident**, not tamper-proof: someone with full database access could disable the trigger and rewrite every hash.
For production, also copy the latest hash to somewhere the database admin cannot write (for example a write-once S3 bucket).

## API (ADMIN role only; all go through the gateway)

| Request | Result |
|---|---|
| `GET /api/v1/admin/audit-logs?actor=&action=&entityType=&entityId=&from=&to=&page=0&size=20` | page of records, newest first (`size` max 100). `from`/`to` are ISO instants, e.g. `2026-11-01T00:00:00Z` |
| `GET /api/v1/admin/audit-logs/{id}` | one record with its details and hashes |
| `GET /api/v1/admin/audit-logs/verify` | `{ valid, checked, firstBrokenSeq, reason, verifiedAt }` |

Errors use the same JSON as Identity and Merchant (`timestamp, status, error, code, message, path, correlationId`).

## What other services should publish

Use the shared envelope (same as Merchant's) on exchange `swiftdrop.events` with a routing key `audit.q` is bound to.

```json
{
  "eventId": "6f9c2d1e-8a41-4c7e-9b52-3d0f6a1e7c10",
  "eventType": "user.role_changed",
  "version": 1,
  "occurredAt": "2026-11-10T08:30:00Z",
  "producer": "identity-service",
  "correlationId": "b41d0c2e-7a1f-4c55-9d2e-0b5f3a1c9e10",
  "aggregateId": "<id of the thing that changed>",
  "data": { "actorId": "<admin user id>", "actorRole": "ADMIN", "newRole": "DISPATCHER" }
}
```

| Event | Published by | Status |
|---|---|---|
| `merchant.registered`, `merchant.approved`, `merchant.rejected`, `merchant.apikey.rotated` | Merchant | already published |
| `user.registered`, `user.locked`, `user.role_changed` | Identity (Member 2) | **to do** |
| `parcel.assigned` | Dispatch (Member 6) | to do |
| `webhook.delivery.exhausted`, `webhook.delivery.replayed` | Webhook (Member 9) | to do |
| `admin.zone.updated`, `admin.pricing.updated`, ... | Pricing and others | to do |

## Run it

```bash
docker compose up -d postgres rabbitmq
cd services/audit
mvn spring-boot:run                 # http://localhost:8091/actuator/health
```

Docker: paste `docker-compose.snippet.yml` into the root compose file, then `docker compose up -d --build audit`.
Try an event: open RabbitMQ at http://localhost:15672, exchange `swiftdrop.events`, "Publish message" with routing key
`merchant.approved` and the JSON above, then call the API through the gateway with an ADMIN token.

## Settings (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `jdbc:postgresql://localhost:5433/audit_db`, `audit_svc`, `dev_service_pw` | database |
| `SPRING_RABBITMQ_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | `localhost`, `5672`, `swiftdrop`, `dev_rabbit_pw` | broker |
| `IDENTITY_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | Identity's public keys |
| `JWT_ISSUER` | `swiftdrop-identity` | expected token issuer |
| `SERVER_PORT` | `8091` | HTTP port |

## Tests

`mvn verify` (Docker must be running; it starts a real PostgreSQL and RabbitMQ). Unit tests cover the hash chain, the sanitizer and the mapper.
`AuditIntegrationTest` covers: event recorded, duplicate ignored, chain verifies, malformed message in the dead-letter queue,
UPDATE/DELETE/TRUNCATE blocked, tampering detected, and the admin API (200, 403 for other roles, 401, 404).
`mvn verify` also enforces 70% line coverage (JaCoCo). The error-handling classes copied from Identity (`common`, `security/Rest*`) are
not counted; they will move to the shared library.

## Known limits

* One consumer thread keeps the chain in order, so throughput is modest. That is fine for audit volume.
* Identity does not publish events yet, so user actions will not appear until it does.
* If the database is down, an event is retried 3 times and then moved to `audit.dlq` (alert on that queue); replay it from the RabbitMQ UI.
