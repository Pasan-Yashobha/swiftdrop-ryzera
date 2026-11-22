# Webhook Delivery Service

Delivers SwiftDrop platform events to merchant webhooks (M9). It consumes events from RabbitMQ, looks up each
merchant's webhook in the Merchant Service, POSTs the event signed with HMAC-SHA256, retries temporary failures
through delay queues, and keeps a delivery log for the admin console and the merchant portal.

Spring Boot 3.5, Java 21, PostgreSQL (`webhook_db` / `webhook_svc`, Flyway), RabbitMQ. Port 8080 in the container
(host 8089 in docker-compose).

## Configuration (environment variables)

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | PostgreSQL | dev: `jdbc:postgresql://localhost:5433/webhook_db`, `webhook_svc` |
| `SPRING_RABBITMQ_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD` | RabbitMQ | dev: `localhost:5672`, `swiftdrop` |
| `IDENTITY_JWKS_URI` | Identity JWKS (RS256) for admin JWTs | none |
| `JWT_ISSUER` | expected `iss`; not checked when empty | none |
| `DEV_JWT_HMAC_SECRET` | HS256 dev tokens, honoured only in the `dev`/`test` profiles | dev default in `application-dev.yml` |
| `INTERNAL_API_TOKEN` | `X-Internal-Token` for `/internal/**` and calls to the Merchant Service (required) | dev: `dev-internal-token` |
| `MERCHANT_SERVICE_URL` | Merchant Service base URL | `http://merchant:8080` (dev: `http://localhost:8083`) |
| `CORS_ALLOWED_ORIGINS` | browser origins of the admin console | `http://localhost:4200` |
| `WEBHOOK_RETRY_DELAYS_MS` | the 5 retry delays, comma separated (also the delay queue TTLs) | `5000,30000,120000,600000,1800000` |

Secrets come only from the environment. The service never stores webhook secrets: it fetches them from the Merchant
Service for every attempt (cached in memory for up to 60 s). Logs never contain secrets, signatures or payloads.

## Endpoints

| Method and path | Auth | Purpose |
|---|---|---|
| `GET /api/v1/admin/webhooks/deliveries?status=&merchantId=&eventType=&page=0&size=20` | JWT, `ADMIN` | list, newest first, `size` at most 100 |
| `GET /api/v1/admin/webhooks/deliveries/{id}` | JWT, `ADMIN` | detail: request headers (signature shortened), payload, response snippet, attempt log |
| `POST /api/v1/admin/webhooks/deliveries/{id}/replay` | JWT, `ADMIN` | one new attempt of a `FAILED` delivery, `202`; `409 INVALID_STATE` otherwise |
| `GET /internal/merchants/{merchantId}/webhook-deliveries?page=&size=` | `X-Internal-Token` | merchant's delivery log, read by the Merchant Service portal |
| `GET /actuator/health`, `/actuator/prometheus` | open | probes and metrics |

Errors use the same JSON as the Merchant Service: `{timestamp, status, error, code, message, path, correlationId, details}`.

## RabbitMQ topology (declared on startup)

| Name | Type | Arguments / bindings |
|---|---|---|
| `swiftdrop.events` | topic exchange, durable | |
| `swiftdrop.dlx` | direct exchange, durable | |
| `swiftdrop.webhook.delay` | direct exchange, durable | |
| `webhook.q` | queue, durable | `x-dead-letter-exchange=swiftdrop.dlx`, `x-dead-letter-routing-key=webhook.dlq`; bound to `swiftdrop.events` with `parcel.created`, `parcel.assigned`, `parcel.cancelled`, `parcel.status.*`, `invoice.generated` |
| `webhook.dlq` | queue, durable | bound to `swiftdrop.dlx` with `webhook.dlq` |
| `webhook.retry.q` | queue, durable | `x-dead-letter-exchange=swiftdrop.dlx`, `x-dead-letter-routing-key=webhook.dlq` |
| `webhook.delay.5s` / `30s` / `2min` / `10min` / `30min` | queues, durable | `x-message-ttl` = 5000 / 30000 / 120000 / 600000 / 1800000, `x-dead-letter-exchange=""`, `x-dead-letter-routing-key=webhook.retry.q`; each bound to `swiftdrop.webhook.delay` with its own name |

Published: `webhook.delivery.exhausted` (data `deliveryId, merchantId, eventId, eventType, attempts, lastHttpStatus,
error`) and `webhook.delivery.replayed` (data `deliveryId, merchantId, eventId, eventType, replayedBy, status`) on
`swiftdrop.events`, with the shared envelope, `producer=webhook-service`, `aggregateId=deliveryId`.

## How an event is delivered

1. `webhook.q` message: not JSON, or missing `eventId`, `eventType`, `version`, `occurredAt`, `producer` or the `data`
   object: rejected without requeue (to `webhook.dlq`). No `data.merchantId`: acked and skipped.
2. `processed_events` insert first; a seen `eventId` is a duplicate and is skipped.
3. `GET {MERCHANT_SERVICE_URL}/internal/merchants/{id}/webhook`. 404, inactive, or not subscribed (`*` matches all):
   skipped, nothing but the processed event is recorded. Merchant Service down / 5xx: the exception rolls the
   transaction back (including the processed event); the listener makes 3 attempts in total (backoff 1 s, then 2 s,
   capped at 10 s), then dead-letters the message.
4. A `PENDING` delivery is stored with the envelope as payload, and the first attempt runs immediately.

Each attempt POSTs the stored payload with `Content-Type: application/json`, `User-Agent: SwiftDrop-Webhooks/1.0`,
`X-SwiftDrop-Event-Id`, `X-SwiftDrop-Event-Type`, `X-SwiftDrop-Timestamp` (unix seconds), `X-SwiftDrop-Signature`
and `X-Correlation-Id`. 5 s timeout, redirects are not followed, only the first 512 bytes of the response are kept.

## Retries

- 2xx: `DELIVERED`.
- Timeout, connection error, 5xx, 429, 408, or Merchant Service unavailable: temporary. The delivery becomes
  `RETRYING` and `{"deliveryId":"..."}` goes to the next delay queue: attempt 2 after 5 s, 3 after 30 s, 4 after
  2 min, 5 after 10 min, 6 after 30 min. When the TTL expires RabbitMQ moves it to `webhook.retry.q`, which this
  service consumes. A retry message for a delivery that is no longer `RETRYING` is ignored.
- Other 4xx, 3xx, webhook removed or disabled: `FAILED` at once.
- After the 6th failure, or any final failure: `FAILED`, the envelope is copied to `webhook.dlq` and
  `webhook.delivery.exhausted` is published.
- Replay (admin): one immediate attempt with a fresh timestamp and signature, same `eventId`. A failure leaves it
  `FAILED` with no automatic retries. Records `lastReplayedBy` and `replayCount` and publishes `webhook.delivery.replayed`.
- A sweeper (every 60 s) re-attempts deliveries stuck in `PENDING`, or `RETRYING` more than 2 min past
  `nextAttemptAt` (e.g. the service stopped mid-attempt or a retry message could not be published).

Every attempt adds a row to `webhook_delivery_attempts` (number, time, HTTP status, duration, outcome, error,
response snippet).

## Verifying a signature (receiver side)

`X-SwiftDrop-Signature` is the lowercase hex HMAC-SHA256 of `timestamp + "." + rawBody`, keyed with the webhook
secret (`whsec_...`), with no prefix (the same as the Merchant Service's test ping). Use the raw body bytes, compare
in constant time, reject timestamps older than 5 minutes, and ignore event ids you have already processed.

```js
// Node.js
const crypto = require('crypto');
function verify(secret, timestamp, rawBody, signature) {
  const expected = crypto.createHmac('sha256', secret).update(`${timestamp}.${rawBody}`).digest('hex');
  const fresh = Math.abs(Date.now() / 1000 - Number(timestamp)) <= 300;
  return fresh && signature.length === expected.length
      && crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(signature));
}
```

```python
# Python
import hmac, hashlib, time
def verify(secret, timestamp, raw_body: bytes, signature):
    expected = hmac.new(secret.encode(), timestamp.encode() + b"." + raw_body, hashlib.sha256).hexdigest()
    return abs(time.time() - int(timestamp)) <= 300 and hmac.compare_digest(expected, signature)
```

## Design notes and deviations

- **Retry queue**: the design text sends expired retries "back into `webhook.q`". They go to a separate
  `webhook.retry.q` instead, carrying only the delivery id, so a retry is never confused with a fresh event (which
  would hit the `processed_events` duplicate check and the merchant lookup again).
- **Final 4xx failures** also produce the `webhook.dlq` copy and `webhook.delivery.exhausted`, so the merchant
  hears about every event their endpoint did not accept.
- **Skipped events** (no webhook, inactive, not subscribed) leave no delivery row, only the `processed_events` row.
- The URL is re-read from the Merchant Service on each attempt, so a merchant who fixes their URL gets the retries.
- Own events are published with publisher confirms but without an outbox. They report state that is already
  stored, so a lost event loses no data.

## Build and test

```bash
mvn -B verify   # unit + integration tests on H2, JaCoCo line coverage gate 70%
```

Tests need no Docker. `WebhookBrokerIntegrationTest` (real RabbitMQ with short TTLs) runs only when Docker is
available.
