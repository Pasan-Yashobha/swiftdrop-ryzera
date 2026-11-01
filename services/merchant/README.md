# Merchant Service

Spring Boot service of the SwiftDrop platform (Member 3). It owns **merchants**: onboarding with admin approval, API
credentials (API keys and OAuth clients), the public merchant API for creating and tracking orders, a sandbox,
bulk CSV upload for the portal, per-merchant rate limits, webhook settings, and the merchant-side read model of
parcel status.

| Document | What it is |
|---|---|
| [`docs/merchant-service/CONTRACT.md`](../../docs/merchant-service/CONTRACT.md) | The binding specification of everything Member 3 builds (API, security, events, configuration). |
| [`docs/merchant-service/DESIGN.md`](../../docs/merchant-service/DESIGN.md) | Design notes with diagrams (use cases, sequences, data model, patterns) for the group design document. |
| [`src/main/resources/merchant-api/openapi.yaml`](src/main/resources/merchant-api/openapi.yaml) | OpenAPI 3.1 description of the public merchant API and of the webhooks SwiftDrop sends. Served at `GET /api/v1/merchant-api/openapi.yaml`. |
| [`frontend/README.md`](../../frontend/README.md) | The Merchant Portal (Angular) that talks to this service. |

| | |
|---|---|
| Stack | Java 21 (also compiles and tests on JDK 17), Spring Boot 3.5, Spring Data JPA, Flyway, Spring Security (OAuth2 resource server), Spring AMQP, commons-csv |
| Database | PostgreSQL `merchant_db`, role `merchant_svc`, schema in `src/main/resources/db/migration/V1__init.sql` |
| Messaging | RabbitMQ: publishes to the topic exchange `swiftdrop.events` through a transactional outbox, consumes parcel events from `merchant.orders.q` |
| Ports | `8080` inside the container, `8083` on the host (docker compose) |
| Health and metrics | `/actuator/health` (with liveness and readiness probes) and `/actuator/prometheus`, both unauthenticated and meant for the internal network only |

## What the service does

* **Merchant onboarding.** A user with the `MERCHANT` role registers one business (`PENDING`). An administrator
  approves, rejects, suspends or reinstates it. Only `APPROVED` merchants may create credentials and orders.
* **Credentials.** API keys (`sd_live_...`, `sd_test_...`, stored as SHA-256 hashes, rotation with a grace period,
  revocation) and OAuth 2.0 client-credentials clients (`mc_...`). `TEST` credentials put every request in sandbox mode.
* **Merchant API** under `/api/v1/merchant-api`: ping, create order (idempotent), get and list orders, advance a sandbox
  order. Authenticated by `X-API-Key` or a bearer access token from `POST /api/v1/oauth/token`, rate limited per merchant.
* **Orders.** Live orders are booked with the Parcel Service (M4) over HTTP; sandbox orders are simulated locally.
  The status of live orders is kept up to date by consuming parcel events (idempotent consumer).
* **Webhooks.** The service stores each merchant's webhook URL, event selection and (encrypted) signing secret, can send a
  signed test ping, proxies the delivery log of the Webhook Delivery Service (M9), and gives M9 the URL and secret
  through an internal endpoint. The actual delivery with retries is done by M9.
* **Events.** `merchant.registered`, `merchant.approved`, `merchant.rejected` and `merchant.apikey.rotated` are written to
  an outbox table in the same transaction as the change and relayed to RabbitMQ.

## Architecture

### Packages

All code is under `lk.swiftdrop.merchant`.

| Package | Responsibility |
|---|---|
| `common` | Error format (`ApiError`, `ErrorCode`, the `ApiException` family, `GlobalExceptionHandler`, `ApiErrorWriter` for filters), `CorrelationIdFilter`, `PiiMasker`, paging (`PageRequests`, `PageResponse`), input normalisation (`Inputs`) and the `@Phone` / `@PlainText` constraints |
| `config` | `AppProperties` (all settings under `swiftdrop.*`, validated at startup), `SecurityConfig` (internal chain and main chain), `JwtDecoderConfig`, `RabbitConfig` (exchanges, JSON converter), `SchedulingConfig`, `ClockConfig` |
| `security` | `JwtRoleConverter`, `CurrentUser`, `InternalTokenFilter`, `MerchantApiPrincipal`, JSON 401/403 handlers, `DevTokenController` (dev profile only) |
| `event` | `EventEnvelope`, `OutboxEvent`, `OutboxService` (enqueue in the caller's transaction), `OutboxRelay` (scheduled publisher with confirms) |
| `merchant` | `Merchant` entity with its status rules, `MerchantService`, `MerchantController` (portal), `AdminMerchantController`, `CurrentMerchantProvider` |
| `credential` | API keys, OAuth clients, token issuing (`AccessTokenService`, `OAuthTokenService`), `MerchantApiSecurityConfig` and `MerchantApiAuthFilter` (merchant API chain), `RateLimiter` / `InMemoryRateLimiter`, ping and OpenAPI controllers |
| `order` | `OrderService` (creation flow), `OrderQueryService`, `SandboxSimulator`, `SandboxOrderService`, `ParcelClient` / `HttpParcelClient`, `BulkCsvImporter`, `MerchantOrderController` (portal), `MerchantApiOrderController` (merchant API), `ParcelEventConsumer` / `ParcelEventHandler`, `OrderMessagingConfig`, `PendingOrderCleanup` |
| `webhook` | `WebhookService`, `SecretCipher` (AES-256-GCM), `UrlSafetyValidator`, `WebhookPinger`, `WebhookSigner`, `DeliveryLogClient`, `WebhookController` (portal), `InternalMerchantController` |

### Security chains

Three independent Spring Security filter chains, tried in this order:

| Order | Matches | Authentication | Authorization |
|---|---|---|---|
| 0 | `/internal/**` | `InternalTokenFilter`: header `X-Internal-Token`, compared as SHA-256 digests in constant time | role `INTERNAL`; never routed through the gateway |
| 1 | `/api/v1/merchant-api/**`, `/api/v1/oauth/**` | `MerchantApiAuthFilter`: `X-API-Key` or `Authorization: Bearer <client-credentials token>`; loads the credential and the merchant on **every** request; then applies the rate limit | public: `GET /merchant-api/openapi.yaml`, `POST /oauth/token`; `GET /merchant-api/ping` needs any valid credential; other `GET` need scope `orders:read`; `POST`/`PUT`/`PATCH`/`DELETE` need `orders:write`; everything else is denied |
| 2 | everything else | Bearer JWT from Identity (M2): RS256 via `IDENTITY_JWKS_URI`, or HS256 with `DEV_JWT_HMAC_SECRET` in the `dev`/`test` profiles only | `/api/v1/merchants/**` needs `ROLE_MERCHANT`, `/api/v1/admin/**` needs `ROLE_ADMIN`, health and Prometheus are open, `POST /api/v1/dev/tokens` is open when the dev controller exists, anything else is denied |

All chains are stateless (no sessions, CSRF off because there are no cookies). `CorrelationIdFilter` runs before them,
so even authentication errors carry `X-Correlation-Id`. 401 and 403 produced by the chains use the same JSON error body
as the controllers.

A merchant user is resolved to a merchant by **ownership** (`merchants.owner_user_id = jwt.sub`). Portal URLs never contain
a merchant id, so a user cannot address another merchant's data.

### Request flows

**Portal request** (for example `POST /api/v1/merchants/me/orders`): `CorrelationIdFilter` → main chain
(JWT verified, role mapped by `JwtRoleConverter`, `ROLE_MERCHANT` required) → `MerchantOrderController` →
`CurrentMerchantProvider.requireApprovedMerchant()` (owner lookup, status check) → `OrderService`.

**Merchant API request** (`POST /api/v1/merchant-api/orders`): `CorrelationIdFilter` → `MerchantApiAuthFilter`
(credential and merchant loaded, `MERCHANT_NOT_APPROVED` if not approved, token bucket, `X-RateLimit-*` headers) →
authorization by scope → `MerchantApiOrderController` → `OrderService.create`:

1. the merchant must be `APPROVED` right now;
2. replay / conflict checks by `Idempotency-Key` and `externalRef` (request fingerprint = SHA-256 of the canonical request);
3. a `PENDING` placeholder row is inserted and committed in its own short transaction (the unique constraints on
   `(merchant_id, idempotency_key)` and `(merchant_id, sandbox, external_ref)` decide between concurrent duplicates;
   a sandbox test and the live order may use the same `externalRef`);
4. LIVE: `ParcelClient.createParcel` (HTTP call to M4) with **no database transaction open**; TEST: `SandboxSimulator`
   (tracking `SBX` + 9 digits, fee `350.00 + 100.00 x weightKg`, no network);
5. the placeholder is completed (`parcel_id`, `tracking_no`, `status=CREATED`, fee). If M4 refuses the order (4xx) the placeholder is
   deleted and `PARCEL_REJECTED` is returned, so the key and reference are free again. If the outcome is unknown (timeout, 5xx,
   unreadable answer, `PARCEL_SERVICE_UNAVAILABLE`) M4 may have booked the parcel, so the placeholder **stays**. M4 de-duplicates on
   its `Idempotency-Key`, which is the placeholder's id: a retry with the same key (or, without a key, the same request and
   `externalRef`) completes that same placeholder once it is older than `swiftdrop.orders.resume-after-seconds` (15 s; before that it
   gets `409 IDEMPOTENCY_KEY_IN_USE`), and M4 returns the original booking instead of making a second one. `PendingOrderCleanup`
   deletes placeholders older than a day.

### Outbox and relay

`MerchantService` and `ApiKeyService` call `OutboxService.enqueue(...)`, which requires an active transaction
(`Propagation.MANDATORY`) and inserts a row in `outbox_events` with the serialised envelope. `OutboxRelay`
(`@Scheduled`, every `swiftdrop.outbox.poll-interval-ms`) reads unpublished rows oldest first, publishes each to
`swiftdrop.events` with routing key = event type, waits for the publisher confirm, then sets `published_at`. On failure it
increments `attempts`, stores a short `last_error` (never the payload) and stops the batch, so order is kept and a broker
outage does not break requests. Delivery is at least once.

### Event consumer

`ParcelEventConsumer` (`@RabbitListener` on `merchant.orders.q`) hands the body to `ParcelEventHandler`, one transaction per
event: parse the envelope (invalid → rejected without requeue → DLQ); ignore events without `data.merchantId`; insert the
`eventId` into `processed_events` (a duplicate is ignored); find the **live** order by `data.trackingNo` (fallback
`data.parcelId`) within that merchant; write `order_status_history`; move `merchant_orders.status` only if `occurredAt` is newer than
`last_event_at` (a late event is recorded in the history but never rolls the status back).

## Endpoints

All paths are under `/api/v1` unless stated otherwise. Roles come from the Identity JWT (`role` or `roles` claim). Errors
have the JSON shape of CONTRACT section 4 (`timestamp, status, error, code, message, path, correlationId, details`), except the
token endpoint, which uses RFC 6749 errors. Lists return `{content, page, size, totalElements, totalPages}` (`size` 1 to 100, default 20).

### Merchant portal (role `MERCHANT`)

| Method and path | Purpose |
|---|---|
| `POST /merchants/register` | register the caller's business (`201`, status `PENDING`; `409 MERCHANT_ALREADY_REGISTERED`) |
| `GET /merchants/me`, `PUT /merchants/me` | read / edit the caller's merchant |
| `POST /merchants/me/resubmit` | ask for a new review after a rejection |
| `GET /merchants/me/api-keys`, `POST /merchants/me/api-keys` | list / create (full key returned once; max 10 active) |
| `POST /merchants/me/api-keys/{id}/rotate` | new key now, old key valid for `graceHours` (0 to 72, default 24) |
| `DELETE /merchants/me/api-keys/{id}` | revoke (`204`, idempotent) |
| `GET /merchants/me/oauth-clients`, `POST /merchants/me/oauth-clients` | list / create (secret returned once; max 10 active) |
| `POST /merchants/me/oauth-clients/{id}/rotate-secret`, `DELETE /merchants/me/oauth-clients/{id}` | new secret (old one dead at once) / revoke |
| `GET /merchants/me/orders` | list (`status`, `search`, `sandbox=true\|false\|all`, `from`, `to`, `page`, `size`) |
| `GET /merchants/me/orders/stats` | dashboard figures (live orders only) |
| `GET /merchants/me/orders/{id}` | order with status history |
| `POST /merchants/me/orders?sandbox=false` | create an order (`Idempotency-Key` optional) |
| `POST /merchants/me/orders/bulk`, `GET /merchants/me/orders/bulk/template` | CSV upload (part `file`; max 500 rows and 1 MiB) / template |
| `GET /merchants/me/webhook`, `PUT /merchants/me/webhook`, `DELETE /merchants/me/webhook` | read / create-or-replace (first save returns the secret once) / delete |
| `POST /merchants/me/webhook/rotate-secret`, `POST /merchants/me/webhook/test` | new secret (once) / signed `webhook.test` ping |
| `GET /merchants/me/webhook/deliveries` | delivery log proxied from M9 (`"available": false` when M9 cannot be reached) |

### Administrators (role `ADMIN`)

| Method and path | Purpose |
|---|---|
| `GET /admin/merchants?status=&search=&page=&size=` | list and search (newest first) |
| `GET /admin/merchants/{id}` | one merchant (adds `ownerUserId`, `decidedBy`) |
| `POST /admin/merchants/{id}/approve` | `PENDING`, `REJECTED` or `SUSPENDED` to `APPROVED`; event `merchant.approved` |
| `POST /admin/merchants/{id}/reject` | `PENDING` to `REJECTED`, body `{"reason": "..."}`; event `merchant.rejected` |
| `POST /admin/merchants/{id}/suspend` | `APPROVED` to `SUSPENDED`, body `{"reason": "..."}`; takes effect on the next API call; no event |
| `POST /admin/merchants/{id}/reinstate` | `SUSPENDED` to `APPROVED` |
| `PUT /admin/merchants/{id}/rate-limit` | body `{"requestsPerMinute": 1..100000}`, effective on the next request |

An invalid transition is `409 INVALID_STATE`.

### Merchant API (API key or access token; documented in `openapi.yaml`)

| Method and path | Scope | Purpose |
|---|---|---|
| `GET /merchant-api/ping` | any | whose credential is this (merchant, environment, scopes) |
| `POST /merchant-api/orders` | `orders:write` | create an order; `201` + `Location`, or `200` + `Idempotent-Replayed: true` |
| `GET /merchant-api/orders?externalRef=&status=&page=&size=` | `orders:read` | list orders of the credential's environment |
| `GET /merchant-api/orders/{trackingNo}` | `orders:read` | one order (own orders and own environment only, else `404`) |
| `POST /merchant-api/sandbox/orders/{trackingNo}/advance` | `orders:write` | move a sandbox order forward or fail it (`403 SANDBOX_ONLY` for LIVE credentials) |
| `GET /merchant-api/openapi.yaml` | public | this API's OpenAPI document |
| `POST /oauth/token` | public (client authenticates inside) | client-credentials grant, `application/x-www-form-urlencoded`, Basic or form-field client authentication |

### Internal (header `X-Internal-Token`, never through the gateway)

| Method and path | Caller | Purpose |
|---|---|---|
| `GET /internal/merchants/{merchantId}/webhook` | M9 Webhook Delivery Service | URL, decrypted secret, `active`, `events`; `404` when not configured; `Cache-Control: no-store` |
| `GET /internal/merchants/{merchantId}` | Notification, Document services | `merchantId, ownerUserId, businessName, status` |

### Other

| Path | Notes |
|---|---|
| `POST /api/v1/dev/tokens` | **`dev` profile only.** `{"sub":"user-1","role":"MERCHANT"}` returns `{"accessToken":"...","expiresIn":3600}` |
| `/actuator/health`, `/actuator/health/**`, `/actuator/prometheus` | open; do not expose them outside the cluster |

## Configuration

All configuration comes from environment variables (Spring relaxed binding also accepts any `swiftdrop.*` property as, for
example, `SWIFTDROP_OUTBOX_BATCH_SIZE`). `application.yml` contains no secrets; development values live in
`application-dev.yml` (profile `dev`). The service **refuses to start** with a message naming the variable when a required
setting is missing or malformed (`AppProperties` is validated).

| Variable | Required | Secret | Default | Purpose |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | no | no | none | `dev` for local development. Never use `dev` in a shared or production environment |
| `SPRING_DATASOURCE_URL` | yes | no | `dev` profile: `jdbc:postgresql://localhost:5432/merchant_db` | JDBC URL, e.g. `jdbc:postgresql://postgres:5432/merchant_db` |
| `SPRING_DATASOURCE_USERNAME` | yes | no | `dev`: `merchant_svc` | database role |
| `SPRING_DATASOURCE_PASSWORD` | yes | **yes** | `dev`: `dev_service_pw` | database password |
| `SPRING_RABBITMQ_HOST` | yes | no | `dev`: `localhost` (Spring's default otherwise) | broker host |
| `SPRING_RABBITMQ_PORT` | no | no | `dev`: `5672` | broker port |
| `SPRING_RABBITMQ_USERNAME` | yes | no | `dev`: `swiftdrop` | broker user |
| `SPRING_RABBITMQ_PASSWORD` | yes | **yes** | `dev`: `dev_rabbit_pw` | broker password |
| `IDENTITY_JWKS_URI` | production | no | empty | JWKS of Identity, e.g. `http://identity:8080/.well-known/jwks.json`; when set, RS256 tokens are verified against it |
| `JWT_ISSUER` | no | no | empty | when set, the `iss` claim of Identity tokens (and of dev tokens) must match |
| `DEV_JWT_HMAC_SECRET` | dev only | **yes** | `dev`: `dev-only-hmac-secret-change-me-0123456789` | HS256 key of development tokens, at least 32 bytes. Only honoured when the `dev` or `test` profile is active and `IDENTITY_JWKS_URI` is empty |
| `INTERNAL_API_TOKEN` | yes | **yes** | `dev`: `dev-internal-token` | shared secret for `/internal/**` and for calls to M4 and M9 |
| `MERCHANT_TOKEN_SECRET` | yes | **yes** | `dev`: `dev-only-merchant-token-secret-0123456789` | HS256 key that signs client-credentials access tokens, at least 32 bytes |
| `WEBHOOK_ENCRYPTION_KEY` | yes | **yes** | `dev`: a fixed 32-byte development key | base64 of exactly 32 bytes (AES-256 key for stored webhook secrets). Generate with `openssl rand -base64 32` |
| `PARCEL_SERVICE_URL` | no | no | `http://parcel:8080`; `dev`: `http://localhost:8084` | Parcel Service (M4) base URL |
| `WEBHOOK_SERVICE_URL` | no | no | `http://webhook:8080`; `dev`: `http://localhost:8089` | Webhook Delivery Service (M9) base URL, for the delivery log |
| `PUBLIC_TRACKING_URL` | no | no | `http://localhost:4200/track` | base of `trackingUrl` in order responses |
| `WEBHOOK_ALLOW_INSECURE_URLS` | no | no | `false` | accept `http://` webhook URLs (development only) |
| `WEBHOOK_ALLOW_PRIVATE_HOSTS` | no | no | `false`; `dev`: `true` | accept loopback and private addresses as webhook targets (development only) |
| `CORS_ALLOWED_ORIGINS` | no | no | `http://localhost:4200` | comma separated origins; matters only when a browser calls the service directly (the portal normally uses its own proxy) |

Settings without a dedicated variable (set them as `SWIFTDROP_...` variables or in a config file):

| Property | Default | Meaning |
|---|---|---|
| `swiftdrop.security.access-token-ttl-seconds` | `3600` (60 to 86400) | lifetime of client-credentials access tokens and dev tokens |
| `swiftdrop.outbox.enabled` | `true` | switch the relay off (tests do) |
| `swiftdrop.outbox.poll-interval-ms` / `batch-size` / `confirm-timeout-ms` | `1000` (min 50) / `50` (1 to 1000) / `5000` (min 100) | relay pause, rows per run, wait for the publisher confirm |
| `swiftdrop.events.exchange` / `dead-letter-exchange` | `swiftdrop.events` / `swiftdrop.dlx` | exchanges |
| `swiftdrop.events.orders-queue` / `orders-dead-letter-queue` | `merchant.orders.q` / `merchant.orders.dlq` | consumer queues |
| `swiftdrop.events.consumer-prefetch` / `consumer-max-retries` | `10` / `3` | unacknowledged messages per consumer / attempts before dead-lettering |
| `swiftdrop.parcel.connect-timeout-ms` / `read-timeout-ms` | `2000` / `5000` | timeouts of the call to M4 |
| `swiftdrop.webhook.test-timeout-ms` / `delivery-log-timeout-ms` | `5000` / `3000` | timeouts of the test ping and of the delivery log lookup |
| `swiftdrop.bulk.max-rows` / `max-bytes` | `500` / `1048576` | CSV upload limits (the servlet multipart limit is 2 MB per file, 3 MB per request) |
| `swiftdrop.orders.pending-placeholder-max-age-seconds` / `cleanup-interval-ms` | `86400` / `60000` | when a stuck `PENDING` placeholder is deleted, and how often the job runs |
| `swiftdrop.orders.resume-after-seconds` | `15` | age after which a retry completes a `PENDING` placeholder itself (must exceed the M4 timeouts) |
| `swiftdrop.retention.interval-ms` | `3600000` | how often published outbox rows (older than 7 days) and handled event ids (older than 14 days) are purged |
| `swiftdrop.scheduling.enabled` | `true` | switch all `@Scheduled` jobs off (tests do) |

## Running locally

Prerequisites: JDK 21 (JDK 17 works with `-Djava.version=17`), Maven, Docker (for Postgres and RabbitMQ), `curl`. The
shell commands below are for a POSIX shell (Git Bash on Windows). In PowerShell use `curl.exe` and `$env:NAME='value'`.

### 1. Start Postgres and RabbitMQ

```bash
# from the repository root
docker compose up -d postgres rabbitmq
docker compose ps          # wait until both are healthy
```

The Postgres init script (`infra/postgres/init/01-init-databases.sh`) creates `merchant_db` and the role `merchant_svc`
(password `dev_service_pw`) the first time the volume is created. RabbitMQ listens on `localhost:5672` (management UI
`http://localhost:15672`, user `swiftdrop`, password `dev_rabbit_pw`).

### 2. Run the service with the dev profile

```bash
# from services/merchant
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# JDK 17 only:  mvn spring-boot:run -Dspring-boot.run.profiles=dev -Djava.version=17
```

The `dev` profile (`application-dev.yml`) points at `localhost` and supplies development-only secrets, so no environment
variable is needed. Flyway creates the schema on first start. The service listens on `http://localhost:8080`
(`--server.port=8083` via `-Dspring-boot.run.arguments=--server.port=8083` if the port is taken).

```bash
curl -s http://localhost:8080/actuator/health
```

### 3. The happy path with curl: register, approve, create a key, create and advance a sandbox order

Development tokens stand in for the Identity service. The helper below extracts a string field from a flat JSON answer.

```bash
BASE=http://localhost:8080/api/v1
field() { sed -E "s/.*\"$1\":\"([^\"]*)\".*/\1/"; }

# a merchant user and an administrator
MERCHANT_TOKEN=$(curl -s -X POST $BASE/dev/tokens -H 'Content-Type: application/json' \
  -d '{"sub":"shop-owner-1","role":"MERCHANT"}' | field accessToken)
ADMIN_TOKEN=$(curl -s -X POST $BASE/dev/tokens -H 'Content-Type: application/json' \
  -d '{"sub":"admin-1","role":"ADMIN"}' | field accessToken)

# 1. register (status PENDING)
MERCHANT_ID=$(curl -s -X POST $BASE/merchants/register \
  -H "Authorization: Bearer $MERCHANT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"businessName":"Lanka Crafts","contactEmail":"owner@lankacrafts.lk","contactPhone":"+94771234567",
       "addressLine":"12 Galle Road","city":"Colombo"}' | field id)
echo "merchant $MERCHANT_ID"

# 2. an administrator approves it (event merchant.approved goes to the outbox)
curl -s -X POST $BASE/admin/merchants/$MERCHANT_ID/approve -H "Authorization: Bearer $ADMIN_TOKEN"

# 3. create a TEST API key; the full key is shown only in this answer
API_KEY=$(curl -s -X POST $BASE/merchants/me/api-keys \
  -H "Authorization: Bearer $MERCHANT_TOKEN" -H 'Content-Type: application/json' \
  -d '{"label":"Local test","environment":"TEST"}' | field apiKey)

# 4. check the key
curl -s $BASE/merchant-api/ping -H "X-API-Key: $API_KEY"

# 5. create a sandbox order (201); the tracking number starts with SBX
ORDER='{"externalRef":"ORDER-1001",
        "recipient":{"name":"Nimali Perera","phone":"+94771234567"},
        "pickup":{"addressLine":"12 Galle Rd","city":"Colombo","postalCode":"00300"},
        "dropoff":{"addressLine":"45 Temple St","city":"Kandy","postalCode":"20000"},
        "parcel":{"weightKg":2.5,"size":"SMALL","description":"Phone case"},"notes":"Call on arrival"}'
TRACKING=$(curl -s -X POST $BASE/merchant-api/orders -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-order-1' -d "$ORDER" | field trackingNo)
echo "tracking $TRACKING"

# the same request again is a replay: 200 and the header Idempotent-Replayed: true, no second order
curl -si -X POST $BASE/merchant-api/orders -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-order-1' -d "$ORDER" | head -n 12

# 6. advance the sandbox order one step at a time (CREATED -> ASSIGNED -> ... -> DELIVERED); 409 INVALID_STATE at the end
curl -s -X POST $BASE/merchant-api/sandbox/orders/$TRACKING/advance -H "X-API-Key: $API_KEY"
curl -s -X POST $BASE/merchant-api/sandbox/orders/$TRACKING/advance -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' -d '{"outcome":"FAILED","reason":"Recipient not available"}'

# 7. read it back
curl -s $BASE/merchant-api/orders/$TRACKING -H "X-API-Key: $API_KEY"
curl -s "$BASE/merchant-api/orders?status=FAILED" -H "X-API-Key: $API_KEY"
```

Note: an order created with a `sd_live_...` key calls the Parcel Service at `PARCEL_SERVICE_URL` (`http://localhost:8084`
in dev) and answers `503 PARCEL_SERVICE_UNAVAILABLE` while that service is not running. Use TEST keys until M4 exists.

**OAuth client credentials instead of a key:**

```bash
CLIENT=$(curl -s -X POST $BASE/merchants/me/oauth-clients -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"label":"Local oauth","environment":"TEST","scopes":["orders:write","orders:read"]}')
CLIENT_ID=$(echo "$CLIENT" | field clientId); CLIENT_SECRET=$(echo "$CLIENT" | field clientSecret)

ACCESS_TOKEN=$(curl -s -u "$CLIENT_ID:$CLIENT_SECRET" -d 'grant_type=client_credentials' \
  $BASE/oauth/token | field access_token)
curl -s $BASE/merchant-api/ping -H "Authorization: Bearer $ACCESS_TOKEN"
```

### 4. Webhook configuration and a test delivery

The service refuses `http://` webhook URLs unless `WEBHOOK_ALLOW_INSECURE_URLS=true`; the `dev` profile already allows
loopback addresses. Restart the service with that variable, then start a receiver that verifies the signature:

```bash
# terminal A: restart the service
WEBHOOK_ALLOW_INSECURE_URLS=true mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Save the receiver as `receiver.js` (anywhere) and run it after you have the secret from step B:

```js
// node receiver.js   (needs WHSEC=<webhook secret>)
const http = require('http'), crypto = require('crypto');
http.createServer((req, res) => {
  const chunks = [];
  req.on('data', c => chunks.push(c));
  req.on('end', () => {
    const body = Buffer.concat(chunks);
    const ts = req.headers['x-swiftdrop-timestamp'];
    const expected = crypto.createHmac('sha256', process.env.WHSEC).update(ts + '.').update(body).digest('hex');
    console.log(req.headers['x-swiftdrop-event-type'], expected === req.headers['x-swiftdrop-signature'] ? 'signature OK' : 'signature BAD');
    res.end('ok');
  });
}).listen(9099);
```

```bash
# terminal B: configure the webhook (the answer contains "secret": shown once), then run the receiver with it
WHSEC=$(curl -s -X PUT $BASE/merchants/me/webhook -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"url":"http://localhost:9099/hook","active":true,"events":["*"]}' | field secret)
WHSEC=$WHSEC node receiver.js &

curl -s -X POST $BASE/merchants/me/webhook/test -H "Authorization: Bearer $MERCHANT_TOKEN"
# {"delivered":true,"httpStatus":200,"durationMs":...,"error":null}   and the receiver prints "webhook.test signature OK"
curl -s $BASE/merchants/me/webhook -H "Authorization: Bearer $MERCHANT_TOKEN"      # secretHint, lastTestAt, lastTestStatus
curl -s $BASE/merchants/me/webhook/deliveries -H "Authorization: Bearer $MERCHANT_TOKEN"   # "available": false until M9 exists
```

Real deliveries of parcel events are done by M9, not by this service; only the `webhook.test` ping is sent from here.

### 5. Look at the outbox

`merchant.registered` and `merchant.approved` were written in the transactions above. With RabbitMQ up they are relayed within a
second:

```bash
docker compose exec postgres psql -U merchant_svc -d merchant_db \
  -c "select event_type, attempts, published_at, last_error from outbox_events order by created_at"
```

The queues `merchant.orders.q` and `merchant.orders.dlq` appear in the RabbitMQ management UI after the service has started.

## Running with Docker Compose

Commands only (the root `docker-compose.yml` defines the `merchant` service, its environment and the host port 8083):

```bash
# from the repository root
docker compose up -d --build postgres rabbitmq merchant      # service on http://localhost:8083
docker compose up -d --build merchant-portal                 # portal on http://localhost:4201
docker compose logs -f merchant
docker compose down                                          # keeps the volumes (data)
```

The compose file starts the service with `SPRING_PROFILES_ACTIVE=${MERCHANT_SPRING_PROFILES:-dev}`, so the dev token endpoint is
reachable on port 8083. Set `MERCHANT_SPRING_PROFILES` and real secrets (`INTERNAL_API_TOKEN`, `MERCHANT_TOKEN_SECRET`,
`WEBHOOK_ENCRYPTION_KEY`, `IDENTITY_JWKS_URI`, a database password via `SERVICE_DB_PASSWORD` in an untracked `.env`) for any shared environment.
`PARCEL_SERVICE_URL` and `WEBHOOK_SERVICE_URL` default to `http://parcel:8080` and `http://webhook:8080`, the compose names of M4 and M9.

Build the image on its own (build context is this folder):

```bash
docker build -t swiftdrop/merchant services/merchant
```

The image runs as a non-root user and has a health check on `/actuator/health/readiness`.

## Tests

```bash
# from services/merchant
mvn verify                       # all tests, JaCoCo report and the coverage gate
mvn -Djava.version=17 verify     # on a machine with JDK 17 only
mvn test -Dtest=OpenApiParityTest,OpenApiEndpointTest    # just the OpenAPI checks
```

* **No Docker is needed.** Integration tests start the whole application on H2 in PostgreSQL mode with the real Flyway
  migration (`application-test.yml`: no broker, schedulers and the outbox relay off). The relay and the consumer are tested against
  mocks and a real listener container driven without a broker.
* **Skipped without Docker:** `OrderBrokerIntegrationTest` (`@Testcontainers(disabledWithoutDocker = true)`). It starts a real
  RabbitMQ container, declares the real topology, publishes parcel events and checks the database, duplicate handling and that a poison
  message lands in `merchant.orders.dlq`. It needs Docker and the image `rabbitmq:3.13-alpine`; run it once on a machine that has them.
* **Not covered by any test:** behaviour against real PostgreSQL (pessimistic locks, `on conflict do nothing`, JPQL group-by were
  only run on H2) and against a real broker or the real M4 and M9 services.
* **Coverage gate:** JaCoCo fails `mvn verify` when bundle line coverage is below `jacoco.minimum` (default `0.70`;
  raise it with `-Djacoco.minimum=0.8`). Configuration classes, `*Properties*`, DTO records, entities, enums named `*Status` or
  `*Environment` and the application class are excluded from the count. The report is `target/site/jacoco/index.html`. The last full run was well above the gate (about 98%).
* `OpenApiParityTest` fails when a controller mapping is missing from `openapi.yaml` or the document describes an operation that does not exist.
* Test helpers for other modules are in `src/test/java/lk/swiftdrop/merchant/testsupport`: `AbstractIntegrationTest`, `TestAuth`
  (`merchant(sub)`, `admin(sub)`, `internal()`), `MerchantFixtures`, `CredentialFixtures`, `TestProperties`.

## Events

Exchange `swiftdrop.events` (topic, durable) and `swiftdrop.dlx` (direct, durable). Persistent JSON messages, content type
`application/json`, publisher confirms on, routing key = event type. Envelope (owned by M9):

```json
{"eventId":"uuid","eventType":"merchant.approved","version":1,"occurredAt":"2026-11-01T08:30:12.123Z",
 "producer":"merchant-service","correlationId":"...","aggregateId":"<merchantId>","data":{}}
```

### Published (outbox)

| `eventType` (= routing key) | `data` | Written when |
|---|---|---|
| `merchant.registered` | `merchantId, businessName` | registration and resubmission |
| `merchant.approved` | `merchantId, decidedBy` | admin approve (also reinstate-by-approve of a rejected or suspended merchant) |
| `merchant.rejected` | `merchantId, decidedBy, reason` | admin reject |
| `merchant.apikey.rotated` | `merchantId, keyPrefix` (of the **new** key, never the key) | API key rotation |

Suspend and reinstate publish nothing. Events carry identifiers only: no email, phone or address.

### Consumed

| Item | Value |
|---|---|
| Queue | `merchant.orders.q`: durable, `x-dead-letter-exchange=swiftdrop.dlx`, `x-dead-letter-routing-key=merchant.orders.dlq`; declared by this service at startup |
| Dead letter queue | `merchant.orders.dlq`: durable, bound to `swiftdrop.dlx` with routing key `merchant.orders.dlq` |
| Bindings on `swiftdrop.events` | `parcel.created`, `parcel.status.*`, `parcel.assigned`, `parcel.cancelled` |
| Prefetch / retries | 10 / 3 attempts with exponential backoff (1 s, 2 s, ... max 10 s), then rejected without requeue (to the DLQ). Unusable messages (not JSON, no `eventId`, `eventType` or `occurredAt`) go to the DLQ at once |
| Acknowledgement | container mode AUTO: ack when the handler returns, reject without requeue after the retries (CONTRACT says "manual ack"; AUTO has the same effect here) |

Mapping: `parcel.status.<s>` to status `<S>` (`picked_up` to `PICKED_UP`, ...), `parcel.assigned` to `ASSIGNED`, `parcel.cancelled`
to `CANCELLED`, `parcel.created` only back-fills `parcel_id`. `data.failureReason` becomes the order's `failureReason`. Needed in `data`:
`merchantId` (a UUID) and `trackingNo` or `parcelId`. Sandbox orders are never touched.

### What M9 must add

* The same queue and bindings in `definitions.json` (identical arguments, otherwise RabbitMQ answers `PRECONDITION_FAILED` when this
  service declares the queue and the consumer does not start): `merchant.orders.q` with the bindings above and the DLQ `merchant.orders.dlq`.
* `data.merchantId` (the merchant's UUID, as returned by this service) and `data.trackingNo` on every `parcel.created` and `parcel.status.*`
  event. `parcel.assigned` and `parcel.cancelled` (Dispatch) may carry only `data.parcelId`: the order is then found by its parcel id,
  which M4 returns when the order is booked.
* Webhook delivery: call `GET /internal/merchants/{merchantId}/webhook` here for URL and secret, sign with
  `HMAC-SHA256(secret, timestamp + "." + body)` (hex, header `X-SwiftDrop-Signature`; timestamp = Unix seconds as in the test ping), apply the
  URL safety rules itself, retry as in the M9 design.
* `GET /internal/merchants/{merchantId}/webhook-deliveries?page=&size=` (header `X-Internal-Token`) returning
  `{content:[{id,eventId,eventType,status,attempts,lastHttpStatus,durationMs,createdAt,lastAttemptAt,error}],page,size,totalElements,totalPages}`.
  The portal shows "unavailable" until it exists.

## Integration assumptions

Copied from CONTRACT sections 6, 8 and 11; each is isolated in one class.

| With | Assumption | Isolated in |
|---|---|---|
| M4 Parcel | `POST {PARCEL_SERVICE_URL}/internal/parcels`, headers `X-Internal-Token`, `X-Correlation-Id`, `Idempotency-Key` (= order id), body `{merchantId, externalRef, recipient, pickup, dropoff, parcel, notes}`, answer `201 {parcelId, trackingNo, status, fee{amount,currency}}`. A 4xx (except 401, 403, 404, 405, 408, 429) is `PARCEL_REJECTED` with a sanitised message; everything else is `PARCEL_SERVICE_UNAVAILABLE`. M4 must publish `parcel.created` and `parcel.status.*` with `merchantId` and `trackingNo` in `data` and honour the idempotency key | `HttpParcelClient` |
| M9 Webhooks | delivery by M9 with the headers and signature above (2xx within 5 s is success; reject timestamps older than 5 minutes; ignore duplicate event ids and out-of-order updates); delivery log at `GET {WEBHOOK_SERVICE_URL}/internal/merchants/{merchantId}/webhook-deliveries` | `DeliveryLogClient`, `WebhookSigner` |
| M2 Identity | RS256 JWTs with `sub` and `role` (`MERCHANT` for merchant sign-ups, `ADMIN` for the console), published JWKS; sign-up with role `MERCHANT` allowed | `JwtDecoderConfig`, `JwtRoleConverter` |
| M1 Gateway | route `/api/v1/merchants/**`, `/api/v1/admin/merchants/**`, `/api/v1/merchant-api/**` and `/api/v1/oauth/token` to `merchant:8080`; **no** Identity-JWT validation on `/merchant-api/**` and `/oauth/token`; never route `/internal/**`; forward `Authorization`, `X-API-Key`, `Idempotency-Key`, `X-Correlation-Id`; do not rate limit `/merchant-api/**` a second time with a lower limit | n/a |
| M10 Admin console | merchant approval UI uses the `/admin/merchants` endpoints | `AdminMerchantController` |

## Operational notes

* **Logging.** Keep `org.hibernate` (SQL and bind parameter logging) and the JDK HTTP client (`jdk.internal.httpclient`)
  at `INFO` or higher in every environment that handles real data: Hibernate prints bound values (addresses, phone numbers,
  hashes) and the JDK client prints request URLs, which can carry webhook tokens. The application code logs no secrets, tokens,
  phone numbers, addresses or email addresses (see `PiiMasker` and the log-leak tests), and `DEBUG` for
  `lk.swiftdrop`, `org.springframework.web`, `.security` and `.amqp` is covered by tests; Hibernate and the JDK client are not. Every log line carries the correlation id (`%X{correlationId}`).
* **Several replicas.**
  * The **rate limiter is per replica** (`InMemoryRateLimiter`): with N replicas a merchant can make up to N times its limit. Replace it
    with a shared implementation of `RateLimiter` (for example Redis) if exact limits matter, or divide the limit by N.
  * The **outbox relay has no row locking**: replicas poll the same unpublished rows and may publish an event twice. This is
    safe because delivery is at least once and consumers deduplicate on `eventId`, but order across replicas is not guaranteed.
    Alternatively run the relay in one replica only (`SWIFTDROP_OUTBOX_ENABLED=false` on the others).
  * `last_used_at` of a credential is written at most about once a minute per credential; the consumer's duplicate detection and
    order idempotency rely on database unique constraints, so they work across replicas. `PendingOrderCleanup` is idempotent.
* **Secrets.** Only environment variables, never `application.yml`. API keys and OAuth secrets are stored as SHA-256 hashes and
  shown once; webhook secrets are stored AES-256-GCM encrypted (random 12-byte IV, key from `WEBHOOK_ENCRYPTION_KEY`) and
  appear only at creation, at rotation and on the internal endpoint. Rotating `WEBHOOK_ENCRYPTION_KEY` makes existing webhook
  secrets unreadable: re-create or rotate each merchant's secret after a key change. Changing `MERCHANT_TOKEN_SECRET` invalidates all
  issued access tokens. Never run the `dev` profile where the service is reachable by others.
* **Schema.** `V1__init.sql` is the schema; change it only with a new `V2__...` migration. Hibernate runs with `ddl-auto: none`
  (`validate` in tests).
* **Webhook SSRF.** URLs are validated when saved and again before each test ping (https only, no credentials, public addresses
  only). The JDK client resolves the host again when it connects, so a DNS-rebinding attacker could still win a race; use network egress
  rules in production. M9 must apply the same checks to its own deliveries.
* **Known limitations.** Parcel booking is exactly-once only if M4 honours the `Idempotency-Key` (the placeholder id); the retry that
  completes a placeholder relies on that. A status event that arrives before `OrderService` has completed the order is ignored
  (and recorded as processed). Bulk upload of live orders is sequential; after the first `PARCEL_SERVICE_UNAVAILABLE` the rest of the file is reported as not
  processed (upload the same file again later: finished rows are replayed, the others completed).

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Startup fails with `INTERNAL_API_TOKEN ... must be set` (or `MERCHANT_TOKEN_SECRET`, `WEBHOOK_ENCRYPTION_KEY`) | A required variable is missing or malformed. Use `-Dspring-boot.run.profiles=dev` locally, or set the variable. `MERCHANT_TOKEN_SECRET` and `DEV_JWT_HMAC_SECRET` need at least 32 bytes; `WEBHOOK_ENCRYPTION_KEY` must be base64 of exactly 32 bytes |
| `No way to verify JWTs is configured` | Neither `IDENTITY_JWKS_URI` nor (`DEV_JWT_HMAC_SECRET` with the `dev`/`test` profile) is set |
| `Connection refused` / `password authentication failed` to Postgres | Postgres is not up (`docker compose up -d postgres`), or the volume was created before `merchant_db` existed; create the database and role manually or recreate the volume (that deletes its data) |
| Flyway `Validate failed: Migrations have failed validation` (checksum mismatch) | `V1__init.sql` was edited after it was applied. Revert it and add a `V2__` migration |
| `404 NOT_FOUND` on `/api/v1/dev/tokens` | The `dev` profile is not active (by design) |
| `401 UNAUTHENTICATED` / `INVALID_TOKEN` on a portal endpoint | No bearer token, expired (dev tokens last one hour), or signed with another secret/issuer |
| `403 FORBIDDEN` on `/merchants/**` or `/admin/**` | The token's `role` is not `MERCHANT` / `ADMIN` |
| `403 MERCHANT_NOT_APPROVED` | The merchant is `PENDING`, `REJECTED` or `SUSPENDED`. An admin must approve or reinstate it |
| `400 MALFORMED_REQUEST` on the merchant API | Both `X-API-Key` and `Authorization: Bearer` were sent, or the body is not JSON |
| `429 RATE_LIMITED` | The merchant's per-minute budget is used up; wait `Retry-After` seconds or raise it with `PUT /admin/merchants/{id}/rate-limit` |
| `503 PARCEL_SERVICE_UNAVAILABLE` | A LIVE key was used and M4 is not reachable at `PARCEL_SERVICE_URL`. Use a TEST key |
| `422 WEBHOOK_URL_REJECTED` | Not `https` (set `WEBHOOK_ALLOW_INSECURE_URLS=true` for local `http://`), credentials in the URL, host does not resolve, or a private address in a non-dev environment |
| `"available": false` in the webhook delivery log | M9's delivery log endpoint is not running or answers an error |
| `/actuator/health` is `DOWN` | RabbitMQ is unreachable (the rabbit health indicator; the container health check uses `/actuator/health/readiness`, which does not include it). The service keeps working; events wait in `outbox_events` (`published_at is null`, see `last_error`) and are sent when the broker returns |
| `PRECONDITION_FAILED - inequivalent arg` in the log at startup | `merchant.orders.q` already exists with different arguments (for example from an M9 `definitions.json`). Make them identical, see Events |
| Tests: `Could not self-attach to current VM` (Mockito) | Surefire's temp directory is on a different drive than the Maven repository, or the disk is full. Keep `TEMP` on the same drive and free some space |
| Maven: `release version 21 not supported` | Only JDK 17 is installed. Build with `-Djava.version=17` |
