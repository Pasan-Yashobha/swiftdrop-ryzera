# Merchant Service and Merchant Portal: build contract (Member 3)

Single source of truth for everything Member 3 builds. Backend lives in `services/merchant`, the portal in `frontend/projects/merchant-portal`.
Anything marked **ASSUMED** is an interface owned by another member that M3 had to guess; it is isolated behind one class so it can be changed in one place.

## 1. Scope (from the work split)

Merchant registration + admin approval workflow, API keys (hashed, rotation, revocation), OAuth2 client-credentials endpoint, webhook configuration, per-merchant rate limits, sandbox mode, bulk CSV upload, merchant portal (registration, orders dashboard, API keys page, webhook settings and delivery log, API docs page).
Tests: API key validation, unapproved merchants blocked from creating orders (plus coverage >= 70%, an event-flow test, authorization tests).

## 2. Stack and conventions

- Java 21 target (`java.version`), code must also compile on JDK 17 (no Java 21-only language/library features). Spring Boot 3.5.x, Maven, package root `lk.swiftdrop.merchant`, artifact `lk.swiftdrop:merchant-service`.
- PostgreSQL (`merchant_db` / `merchant_svc`), Flyway (`db/migration/V1__init.sql` is the schema, do not edit it casually; add `V2__...` for changes), Spring Data JPA, `spring.jpa.hibernate.ddl-auto=none` at runtime and `validate` in tests.
- RabbitMQ via Spring AMQP, JSON envelope defined by M9 (section 7). No Redis.
- Tests: JUnit 5, Mockito, MockMvc, AssertJ, H2 (PostgreSQL mode) with the real Flyway migration. Tests must pass with **no Docker**. Testcontainers-based tests are allowed only with `@Testcontainers(disabledWithoutDocker = true)`.
- JaCoCo gate: line coverage >= 0.70 for the bundle (`mvn verify`). Config classes, DTO records, entities and the application class may be excluded.
- Port 8080 inside the container, `8083` on the host. Actuator exposes `health` and `prometheus`.
- All secrets come from environment variables (section 9). No secret defaults in `application.yml`; local dev defaults live in `application-dev.yml` and `docker-compose.yml` only.
- Never log: passwords, API keys, client secrets, webhook secrets, tokens, phone numbers, full addresses, email addresses. Use `PiiMasker`.
- IDs are UUIDs generated in Java. Timestamps are `Instant` (UTC), serialised as ISO-8601 strings. Money is `BigDecimal` with 2 decimals, currency `LKR`.
- Optimistic locking (`@Version`) on `merchants` and `merchant_orders`.

### Package layout

```
lk.swiftdrop.merchant
  MerchantServiceApplication
  common/      ApiError, ApiException (+ NotFound/Conflict/BadRequest/Forbidden/Unauthorized/TooManyRequests/ServiceUnavailable/Unprocessable),
               GlobalExceptionHandler, CorrelationIdFilter, PiiMasker, PageResponse
  config/      AppProperties (@ConfigurationProperties "swiftdrop"), SecurityConfig, JwtDecoderConfig, RabbitConfig, SchedulingConfig, ClockConfig
  security/    MerchantApiPrincipal, CurrentUser, JwtRoleConverter, InternalTokenFilter
  event/       EventEnvelope, OutboxEvent, OutboxRepository, OutboxService, OutboxRelay
  merchant/    Merchant, MerchantStatus, MerchantRepository, MerchantService, CurrentMerchantProvider,
               MerchantController, AdminMerchantController, DTOs
  credential/  ApiKey, OAuthClient, repositories, ApiKeyService, OAuthClientService, KeyGenerator, SecretHasher,
               AccessTokenService, OAuthTokenController, ApiKeyController, OAuthClientController,
               MerchantApiAuthFilter, MerchantApiSecurityConfig, RateLimiter, InMemoryRateLimiter, DTOs
  order/       MerchantOrder, OrderStatus, OrderStatusHistory, ProcessedEvent, repositories, OrderService, OrderQueryService,
               OrderStatsService, ParcelClient, HttpParcelClient, SandboxSimulator, ParcelEventConsumer, BulkCsvImporter,
               MerchantOrderController (portal), MerchantApiOrderController (merchant-api), DTOs
  webhook/     WebhookConfig, WebhookConfigRepository, WebhookService, SecretCipher, UrlSafetyValidator, WebhookPinger,
               DeliveryLogClient, WebhookController, InternalMerchantController, DTOs
```

Slice ownership (so parallel work never edits the same file): **S1** `common config security event merchant` + `pom.xml`, `application*.yml`, `Dockerfile`; **S2** `credential`; **S3** `order`; **S4** `webhook`.
Slices S2-S4 only add new files (plus tests). They must not edit S1 files except where this document says a hook exists.

## 3. Security model

Two independent filter chains.

1. **Main chain** (`@Order(2)`, S1) for `/api/v1/**` except the merchant API: stateless, OAuth2 resource server, Bearer JWT issued by Identity (M2).
   - Decoder: if `swiftdrop.security.identity-jwks-uri` is set use `NimbusJwtDecoder.withJwkSetUri(..)` (RS256); else if `swiftdrop.security.dev-hmac-secret` is set (dev profile only) use an HS256 decoder; else fail startup with a clear message. Optional issuer validation when `jwt-issuer` is non-empty.
   - Claims: `sub` = user id, role in claim `role` (string) **or** `roles` (array), values `MERCHANT`, `ADMIN`, `DISPATCHER`, `CUSTOMER`, `COURIER`. Authorities are `ROLE_<value>`.
   - `/api/v1/merchants/**` need `ROLE_MERCHANT`. `/api/v1/admin/**` needs `ROLE_ADMIN`. `/actuator/health/**` and `/actuator/prometheus` are open (network-internal). `/internal/**` needs the internal token (header `X-Internal-Token`, constant-time compare) and nothing else. Everything else is denied.
   - A merchant user is resolved to a merchant by **ownership**: `merchants.owner_user_id = jwt.sub`. The JWT does not carry a merchant id. Every portal endpoint works on "my merchant" (`/merchants/me/...`), so a user can never address another merchant's data (no merchantId in portal URLs).
2. **Merchant-API chain** (`@Order(1)`, S2) matching `/api/v1/merchant-api/**`: stateless, no JWT from Identity. `MerchantApiAuthFilter` accepts `X-API-Key: <key>` **or** `Authorization: Bearer <client-credentials token>`; then applies the per-merchant rate limit. On success it places a `MerchantApiPrincipal(merchantId, credentialId, environment, scopes)` in the security context (authority `ROLE_MERCHANT_API` plus `SCOPE_orders:write` / `SCOPE_orders:read`). `GET /api/v1/merchant-api/openapi.yaml` is public. `/api/v1/oauth/token` is public (the client authenticates inside the request) and is matched by this chain too.
3. The merchant must be `APPROVED` for any credential issuance and any order creation. `PENDING`, `REJECTED` and `SUSPENDED` merchants get `403 MERCHANT_NOT_APPROVED` on the merchant API (even with a valid key) and on portal credential/order-creation endpoints. Suspending a merchant must immediately block existing keys (the filter loads the merchant status on each request).

### API keys

- Format `sd_<live|test>_<id8>_<secret43>`: `id8` = 8 chars `[a-z0-9]`, `secret43` = 43 chars base64url of 32 random bytes (`SecureRandom`). `key_prefix` = `sd_live_ab12cd34` (public, unique), `key_hash` = lowercase hex SHA-256 of the **whole key**. Lookup by prefix, compare hashes with `MessageDigest.isEqual`. The full key is returned exactly once at creation/rotation.
- `TEST` keys put the request in sandbox mode (section 6). `LIVE` keys create real parcels.
- Rotation: `POST .../rotate` creates a new key (same label/environment, `rotated_from_id` set) and sets the old key `expires_at = now + graceHours` (default 24, 0 means revoke now). Expired/revoked keys are rejected with `401 INVALID_API_KEY`. Publishes `merchant.apikey.rotated` (data: `merchantId`, `keyPrefix` of the **new** key, never the key).
- `last_used_at` is updated at most once per minute per key (avoid a write per request).

### OAuth2 client credentials (RFC 6749 section 4.4)

- `POST /api/v1/oauth/token`, `Content-Type: application/x-www-form-urlencoded`, `grant_type=client_credentials`, optional `scope` (space separated, subset of the client's scopes). Client authentication: HTTP Basic (`client_id:client_secret`) **or** form fields `client_id` / `client_secret`.
- Success `200`: `{"access_token":"<jwt>","token_type":"Bearer","expires_in":3600,"scope":"orders:write orders:read"}` with `Cache-Control: no-store`, `Pragma: no-cache`.
- Errors per RFC 6749 section 5.2: JSON `{"error":"invalid_request|invalid_client|invalid_grant|unauthorized_client|unsupported_grant_type|invalid_scope","error_description":"..."}`; `invalid_client` is `401` (with `WWW-Authenticate: Basic` if Basic was attempted), others `400`. Never reveal whether the client id exists.
- Client id `mc_` + 20 chars `[a-z0-9]`; secret 43 chars base64url; `secret_hash` = hex SHA-256. Secret shown once.
- Token: JWT, HS256 signed with `swiftdrop.security.merchant-token-secret` (>= 32 bytes), claims `iss=swiftdrop-merchant-service`, `sub=<client_id>`, `merchantId`, `credentialId` (oauth_clients.id), `env` (LIVE|TEST), `scope`, `iat`, `exp`, `jti`. Only this service validates these tokens. On every request the filter re-checks that the client is still ACTIVE and the merchant APPROVED.
- A token request from a client whose merchant is not `APPROVED` gets `401 invalid_client` (the token endpoint must not leak merchant state).

### Rate limiting

Token bucket per merchant id (in memory, `RateLimiter` interface so it can be swapped for Redis later). Capacity and refill = `merchants.rate_limit_per_minute` (default 60/min). Applies to all merchant-API requests (not the token endpoint). Headers on every merchant-API response: `X-RateLimit-Limit`, `X-RateLimit-Remaining`; on `429`: `Retry-After` (seconds) and error code `RATE_LIMITED`. Changing the limit via the admin endpoint takes effect on the next request.

## 4. Error format (all endpoints except `/oauth/token`)

```json
{
  "timestamp": "2026-11-01T08:30:12.123Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "PARCEL_REJECTED",
  "message": "human readable, safe for end users, no stack traces, no PII, no SQL",
  "path": "/api/v1/merchant-api/orders",
  "correlationId": "b41d0c2e-...",
  "details": [{"field": "dropoff.city", "message": "must not be blank"}]
}
```

`details` only for validation errors. `X-Correlation-Id` is read from the request (generated if missing), put in MDC (`correlationId`), echoed in the response, copied into events and outgoing calls.
Codes: `VALIDATION_FAILED` 400, `MALFORMED_REQUEST` 400, `UNAUTHENTICATED` 401, `INVALID_API_KEY` 401, `INVALID_TOKEN` 401, `FORBIDDEN` 403, `MERCHANT_NOT_APPROVED` 403, `SANDBOX_ONLY` 403, `NOT_FOUND` 404, `MERCHANT_NOT_REGISTERED` 404, `MERCHANT_ALREADY_REGISTERED` 409, `DUPLICATE_EXTERNAL_REF` 409, `IDEMPOTENCY_KEY_IN_USE` 409, `INVALID_STATE` 409, `OPTIMISTIC_LOCK` 409, `IDEMPOTENCY_KEY_REUSED` 422, `PARCEL_REJECTED` 422, `WEBHOOK_URL_REJECTED` 422, `WEBHOOK_NOT_CONFIGURED` 409, `PAYLOAD_TOO_LARGE` 413, `RATE_LIMITED` 429, `PARCEL_SERVICE_UNAVAILABLE` 503, `INTERNAL_ERROR` 500 (generic message only).
Unauthenticated and forbidden responses from the security chains use the same JSON shape (custom `AuthenticationEntryPoint` / `AccessDeniedHandler`).

## 5. REST API

All paths are under `/api/v1`. `P` = portal (Identity JWT, `ROLE_MERCHANT`), `A` = admin (`ROLE_ADMIN`), `M` = merchant API credential, `I` = internal token. Pagination: `?page=0&size=20` (size max 100) returns
`{"content":[...],"page":0,"size":20,"totalElements":123,"totalPages":7}`.

### 5.1 Merchant profile (S1)

| Method and path | Auth | Notes |
|---|---|---|
| `POST /merchants/register` | P | Body `RegisterMerchantRequest`. `201 MerchantResponse` status `PENDING`. `409 MERCHANT_ALREADY_REGISTERED` if the caller owns one. Outbox event `merchant.registered`. |
| `GET /merchants/me` | P | `MerchantResponse`, `404 MERCHANT_NOT_REGISTERED` if none. |
| `PUT /merchants/me` | P | Update `businessName, contactEmail, contactPhone, addressLine, city, website, businessRegNo`. Status, rate limit not editable. A `REJECTED` merchant editing and calling `POST /merchants/me/resubmit` goes back to `PENDING`. |
| `POST /merchants/me/resubmit` | P | Only from `REJECTED`; sets `PENDING`, clears decision fields, emits `merchant.registered`. |

`RegisterMerchantRequest`: `businessName` (2..150), `businessRegNo?` (<= 64), `contactEmail` (email, <= 254), `contactPhone` (E.164-ish after stripping spaces/dashes/brackets: `^\+?[0-9]{9,15}$`), `addressLine` (<= 255), `city` (<= 100), `website?` (http/https URL <= 255).
`MerchantResponse`: `id, businessName, businessRegNo, contactEmail, contactPhone, addressLine, city, website, status, decisionReason, decidedAt, rateLimitPerMinute, createdAt, updatedAt`. (`decidedBy` is only in the admin response.)

### 5.2 Admin (S1, used by M10's admin console)

| Method and path | Notes |
|---|---|
| `GET /admin/merchants?status=&search=&page=&size=` | `search` matches business name / contact email (case-insensitive contains). Sorted `createdAt` desc. Returns `AdminMerchantResponse` (= MerchantResponse + `ownerUserId`, `decidedBy`). |
| `GET /admin/merchants/{id}` | |
| `POST /admin/merchants/{id}/approve` | From `PENDING` (or `REJECTED`/`SUSPENDED` -> `APPROVED` allowed). Sets decided_by = jwt.sub. Event `merchant.approved`. |
| `POST /admin/merchants/{id}/reject` | Body `{"reason": "..."}` (required, <= 500). From `PENDING`. Event `merchant.rejected`. |
| `POST /admin/merchants/{id}/suspend` | Body `{"reason": "..."}`. From `APPROVED`. No event required. |
| `POST /admin/merchants/{id}/reinstate` | `SUSPENDED` -> `APPROVED`. |
| `PUT /admin/merchants/{id}/rate-limit` | Body `{"requestsPerMinute": 1..100000}`. |

Invalid transition: `409 INVALID_STATE`. Approving/rejecting an already decided merchant is not idempotent-silent: it is `409` unless the transition table above allows it.

### 5.3 Credentials (S2)

| Method and path | Auth | Notes |
|---|---|---|
| `GET /merchants/me/api-keys` | P | List (no secrets): `id, label, environment, keyPrefix, status, expiresAt, lastUsedAt, createdAt, revokedAt`. |
| `POST /merchants/me/api-keys` | P | Body `{"label":"Shop backend","environment":"LIVE"}` (approved only). `201` = list item + `apiKey` (full key, once). Max 10 ACTIVE keys per merchant (`409 INVALID_STATE`). |
| `POST /merchants/me/api-keys/{id}/rotate` | P | Body `{"graceHours": 24}` (0..72). `201` new key + `apiKey`. Old key expires after grace. Only ACTIVE keys can be rotated. |
| `DELETE /merchants/me/api-keys/{id}` | P | Revoke. `204`. Idempotent. |
| `GET /merchants/me/oauth-clients` | P | `id, clientId, label, environment, scopes, status, lastUsedAt, createdAt, revokedAt`. |
| `POST /merchants/me/oauth-clients` | P | Body `{"label":"..","environment":"TEST","scopes":["orders:write","orders:read"]}`. `201` + `clientSecret` once. Max 10 ACTIVE. |
| `POST /merchants/me/oauth-clients/{id}/rotate-secret` | P | `200` + new `clientSecret`; old secret dead immediately. |
| `DELETE /merchants/me/oauth-clients/{id}` | P | Revoke, `204`. |
| `POST /oauth/token` | public | Section 3. |

A merchant can only see/modify its own credentials: looking up another merchant's id returns `404 NOT_FOUND` (not 403).

### 5.4 Orders

`CreateOrderRequest` (used by portal and merchant API):

```json
{
  "externalRef": "ORDER-1001",
  "recipient": {"name": "Nimali Perera", "phone": "+94771234567"},
  "pickup":  {"addressLine": "12 Galle Rd", "city": "Colombo", "postalCode": "00300"},
  "dropoff": {"addressLine": "45 Temple St", "city": "Kandy", "postalCode": "20000"},
  "parcel":  {"weightKg": 2.5, "size": "SMALL", "description": "Phone case"},
  "notes": "Call on arrival"
}
```

Validation: `recipient.name` 1..120, `recipient.phone` as above, `addressLine` 1..300, `city` 1..100, `postalCode?` <= 16, `weightKg` 0.01..50.00 (2 decimals), `size` in `SMALL|MEDIUM|LARGE`, `description?` <= 200, `notes?` <= 500, `externalRef?` 1..100 `[A-Za-z0-9._:/#-]`. Strings are trimmed; control characters rejected.
`OrderResponse`: `id, trackingNo, externalRef, status, sandbox, source, trackingUrl, recipient{name,phone}, pickup{...}, dropoff{...}, parcel{weightKg,size,description}, notes, fee{amount,currency}|null, failureReason, createdAt, updatedAt`.
`OrderDetailResponse` = `OrderResponse` + `history: [{fromStatus,toStatus,occurredAt,note}]`.
`trackingUrl` = `swiftdrop.tracking.public-url + "/" + trackingNo`.

Portal (S3, `P`, merchant must be APPROVED for POST, any status can GET but only own data):

| Method and path | Notes |
|---|---|
| `GET /merchants/me/orders?status=&search=&sandbox=&from=&to=&page=&size=` | `search` = tracking no / external ref / recipient name contains. `from`/`to` are ISO dates on `createdAt`. Default hides nothing; `sandbox` filter optional (default: live orders only, `sandbox=true` sandbox only, `sandbox=all` both). Sorted `createdAt` desc. |
| `GET /merchants/me/orders/{id}` | `OrderDetailResponse`. Another merchant's id -> `404`. |
| `GET /merchants/me/orders/stats` | `{"total":..,"byStatus":{"CREATED":3,...},"delivered":..,"failed":..,"inProgress":..,"last7Days":[{"date":"2026-10-30","count":4}]}` live orders only. |
| `POST /merchants/me/orders?sandbox=false` | Body `CreateOrderRequest`, header `Idempotency-Key` optional. `201 OrderResponse`. |
| `POST /merchants/me/orders/bulk` | `multipart/form-data` part `file` (CSV). Max `swiftdrop.bulk.max-rows` (500) rows and `max-bytes` (1 MiB). `200 BulkResult`. |
| `GET /merchants/me/orders/bulk/template` | `text/csv` template file with the header row and one example row. |

CSV header (exact, UTF-8, comma separated, RFC 4180 quoting, BOM tolerated):
`externalRef,recipientName,recipientPhone,pickupAddress,pickupCity,pickupPostalCode,dropoffAddress,dropoffCity,dropoffPostalCode,weightKg,size,description,notes`
`BulkResult`: `{"totalRows":3,"created":2,"failed":1,"results":[{"row":2,"status":"CREATED","trackingNo":"SD24000123","orderId":"..."},{"row":3,"status":"FAILED","errors":["dropoffCity: must not be blank"]}]}`. Row numbers are 1-based file lines (header = line 1). A bad file (wrong header, too many rows, too big) is rejected whole with `400 VALIDATION_FAILED` / `413 PAYLOAD_TOO_LARGE`. Cells starting with `=`, `+`, `-`, `@` are accepted but must be stored as is and never re-exported unescaped (CSV injection). Each row is created through the same `OrderService` path with idempotency key `bulk:<sha256(file)[0..16]>:<row>` so re-uploading the same file does not duplicate.

Merchant API (S3, `M`):

| Method and path | Scope | Notes |
|---|---|---|
| `GET /merchant-api/ping` | any | `{"merchantId":"..","businessName":"..","environment":"LIVE","scopes":[..]}` to test credentials. |
| `POST /merchant-api/orders` | `orders:write` | Body `CreateOrderRequest`, header `Idempotency-Key` (recommended). `201` new, `200` + header `Idempotent-Replayed: true` for a replay with identical payload. |
| `GET /merchant-api/orders/{trackingNo}` | `orders:read` | By tracking number (only own orders, else `404`). |
| `GET /merchant-api/orders?externalRef=&status=&page=&size=` | `orders:read` | Only orders of the credential's environment (sandbox for TEST, live for LIVE). |
| `POST /merchant-api/sandbox/orders/{trackingNo}/advance` | `orders:write` | TEST credentials only (`403 SANDBOX_ONLY` otherwise). Body optional `{"outcome":"FAILED","reason":".."}`. Moves a sandbox order along `CREATED -> ASSIGNED -> PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED`, or to `FAILED`. Terminal -> `409 INVALID_STATE`. No broker events, no webhook. |
| `GET /merchant-api/openapi.yaml` | public | The published OpenAPI document (classpath `static/merchant-api/openapi.yaml`). |

Idempotency (all order creation paths): key unique per merchant. Same key + same canonical payload hash -> return the stored order. Same key + different payload -> `422 IDEMPOTENCY_KEY_REUSED`. Same key while the first request is still in flight -> `409 IDEMPOTENCY_KEY_IN_USE` with `Retry-After: 1`. `externalRef` is unique per merchant -> `409 DUPLICATE_EXTERNAL_REF`.

### 5.5 Webhook settings (S4, `P`)

| Method and path | Notes |
|---|---|
| `GET /merchants/me/webhook` | `{"configured":false}` or `{"configured":true,"url":"..","active":true,"events":["*"],"secretHint":"whsec_ab12...","lastTestAt":..,"lastTestStatus":".."}`. Never returns the secret. |
| `PUT /merchants/me/webhook` | Body `{"url":"https://shop.example.com/hooks/swiftdrop","active":true,"events":["*"]}`. First call creates the config and the response additionally contains `"secret":"whsec_..."` (shown once). Later calls keep the secret. `events` is `["*"]` or a list of `parcel.created`, `parcel.status.picked_up`, ... `parcel.cancelled`. Requires APPROVED. |
| `POST /merchants/me/webhook/rotate-secret` | `200 {"secret":"whsec_..."}` shown once. |
| `POST /merchants/me/webhook/test` | Sends a signed `webhook.test` event to the URL (5 s timeout, same headers/signature as M9, section 8) and returns `{"delivered":true,"httpStatus":200,"durationMs":123,"error":null}`; stores `lastTestAt/lastTestStatus`. `409 WEBHOOK_NOT_CONFIGURED` if none. |
| `DELETE /merchants/me/webhook` | `204`, removes the config. |
| `GET /merchants/me/webhook/deliveries?page=&size=` | Proxy to M9 (**ASSUMED**, section 8). Response `{"available":true,"content":[{"id","eventId","eventType","status","attempts","lastHttpStatus","durationMs","createdAt","lastAttemptAt","error"}],"page":0,"size":20,"totalElements":0,"totalPages":0}`. If M9 is unreachable or returns 404: `200` with `"available":false` and empty content (never a 5xx). |

URL rules (`UrlSafetyValidator`): scheme must be `https` (http only when `swiftdrop.webhook.allow-insecure-urls=true`), no userinfo, host required, port 1..65535, <= 500 chars, and the resolved addresses must not be loopback, link-local, site-local, multicast, any-local or metadata (169.254.169.254) unless `swiftdrop.webhook.allow-private-hosts=true`. Violations: `422 WEBHOOK_URL_REJECTED` with a safe message.

### 5.6 Internal (S4, `I`, never routed through the gateway)

| Method and path | Notes |
|---|---|
| `GET /internal/merchants/{merchantId}/webhook` | For M9's Webhook Delivery Service. `200 {"merchantId":"..","url":"..","secret":"whsec_..","active":true,"events":["*"]}` (secret decrypted), `404` if the merchant has no config. Must not be cached by intermediaries (`Cache-Control: no-store`). |
| `GET /internal/merchants/{merchantId}` | `200 {"merchantId":"..","ownerUserId":"..","businessName":"..","status":"APPROVED"}` for Notification Service / Document Service. |

## 6. Orders and sandbox behaviour (S3)

- `OrderService.create(...)` flow: (1) merchant must be APPROVED; (2) idempotency / externalRef checks; (3) insert placeholder row with `status=PENDING` in its own committed transaction (this is what makes concurrent duplicates safe: unique constraints decide); (4) LIVE -> `ParcelClient.createParcel(...)` **outside any DB transaction**; TEST -> `SandboxSimulator` (tracking `SBX` + 9 random digits, fee `350.00 + 100.00 * weightKg`, status `CREATED`, no network); (5) update the row with `parcelId, trackingNo, status=CREATED, fee`; on parcel rejection or failure delete the placeholder and surface `PARCEL_REJECTED` / `PARCEL_SERVICE_UNAVAILABLE` so the client can retry with the same key.
- Amendment after review: a 4xx from M4 deletes the placeholder; any other failure keeps it (M4 may have booked) and the next request with the same key (or the same request and externalRef) completes the same placeholder after `swiftdrop.orders.resume-after-seconds` (15). A scheduled job deletes placeholders older than a day. `external_ref` is unique per (merchant, sandbox flag).
- `ParcelClient` (**ASSUMED M4 contract**): `POST {swiftdrop.parcel.base-url}/internal/parcels`, headers `X-Internal-Token`, `X-Correlation-Id`, `Idempotency-Key` (= order id), body
  `{"merchantId":"..","externalRef":"..","recipient":{..},"pickup":{..},"dropoff":{..},"parcel":{..},"notes":".."}` -> `201 {"parcelId":"..","trackingNo":"SD24000123","status":"CREATED","fee":{"amount":450.00,"currency":"LKR"}}`. 4xx -> `PARCEL_REJECTED` (message from M4 sanitised and truncated, never raw upstream bodies); 5xx / timeout / connection error -> `PARCEL_SERVICE_UNAVAILABLE`. Timeouts from config. Only one class (`HttpParcelClient`) knows this shape.
- Status read model (event consumer, section 7): the dashboard reads `merchant_orders` + `order_status_history`; there is no synchronous call to M4 for reads.

## 7. Events

Exchange `swiftdrop.events` (topic, durable) and `swiftdrop.dlx` (direct, durable). Persistent JSON messages, content type `application/json`, publisher confirms on, routing key = `eventType`. Envelope (owned by M9, must match exactly):

```json
{"eventId":"uuid","eventType":"merchant.approved","version":1,"occurredAt":"2026-11-01T08:30:12Z",
 "producer":"merchant-service","correlationId":"...","aggregateId":"<merchantId>","data":{...}}
```

**Published (S1 outbox + relay; S2 uses `OutboxService` for the key event):**

| eventType | data |
|---|---|
| `merchant.registered` | `merchantId, businessName` |
| `merchant.approved` | `merchantId, decidedBy` |
| `merchant.rejected` | `merchantId, decidedBy, reason` |
| `merchant.apikey.rotated` | `merchantId, keyPrefix` |

Events carry ids only (no email/phone/address). Outbox: row written in the same transaction as the change; `OutboxRelay` (`@Scheduled(fixedDelay = poll-interval-ms)`) publishes unpublished rows in `created_at` order with publisher confirm, marks `published_at`, increments `attempts` and stores a short `last_error` on failure (never the payload). Publishing when RabbitMQ is down must not break the request path.

**Consumed (S3):** queue `merchant.orders.q` (durable, `x-dead-letter-exchange=swiftdrop.dlx`, DLQ `merchant.orders.dlq`, manual ack, prefetch 10) declared by this service on startup, bound to `swiftdrop.events` with keys `parcel.created`, `parcel.status.*`, `parcel.assigned`, `parcel.cancelled`. M9 should add the same queue/bindings to `definitions.json`.
Handling (`ParcelEventConsumer`): parse envelope; unknown/invalid JSON -> reject without requeue (goes to DLQ); skip events without `data.merchantId`; skip duplicates via `processed_events` (insert `event_id` first; unique violation = duplicate = ack and ignore); find the order by `data.trackingNo` (fallback `data.parcelId`) belonging to that `merchantId`, ignore if not found; map `parcel.status.<s>` -> `OrderStatus` (`toStatus`), `parcel.assigned` -> `ASSIGNED`, `parcel.cancelled` -> `CANCELLED`; write `order_status_history` row (unique `(order_id,event_id)`); update `merchant_orders.status`, `failure_reason` (from `data.failureReason`), `last_event_at` **only if** `occurredAt` is newer than `last_event_at` (late events are recorded in history but do not move the status backwards); `parcel.created` just back-fills `parcel_id` if missing. Transient failures (DB down) -> nack with requeue limited by a retry header, then DLQ; never loop forever.

## 8. Webhooks

- Delivery is done by M9 (Webhook Delivery Service), not by M3. M3 owns the configuration and the secret.
- M9 headers: `X-SwiftDrop-Event-Id`, `X-SwiftDrop-Event-Type`, `X-SwiftDrop-Timestamp`, `X-SwiftDrop-Signature` = hex `HMAC-SHA256(secret, timestamp + "." + body)`; success = any 2xx within 5 s; merchants must reject timestamps older than 5 minutes and ignore duplicate event ids and out-of-order updates (compare `occurredAt`). The API docs page and the OpenAPI document must say this and include verification examples (Node.js, Python, Java, PHP or cURL-free pseudocode).
- Secret: `whsec_` + 43 chars base64url (32 random bytes). Stored encrypted (AES-256-GCM, random 12-byte IV per encryption, stored as base64 of `iv || ciphertext+tag`) with key `swiftdrop.webhook.encryption-key` (base64, exactly 32 bytes; startup fails otherwise). `secret_hint` = first 10 chars + `...`. The secret never appears in logs, errors, `toString()` or API responses except at creation/rotation and the internal endpoint.
- `webhook/test` uses the same signing code path as M9 documents (`WebhookSigner` in M3 is used only for the test ping; payload `{"eventId":"<uuid>","eventType":"webhook.test","version":1,"occurredAt":"...","producer":"merchant-service","data":{"merchantId":".."}}`). Redirects are not followed. Response bodies are not stored, only the status code.
- **ASSUMED (M9)**: `GET {swiftdrop.webhook.delivery-log-url}/internal/merchants/{merchantId}/webhook-deliveries?page=&size=` with `X-Internal-Token` returning the same page shape as 5.5. Implemented only in `DeliveryLogClient`.

## 9. Configuration (environment variables)

| Variable | Required | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | yes | `jdbc:postgresql://postgres:5432/merchant_db`, `merchant_svc`, `SERVICE_DB_PASSWORD` |
| `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | yes | broker |
| `IDENTITY_JWKS_URI` | prod | e.g. `http://identity:8080/.well-known/jwks.json` |
| `JWT_ISSUER` | no | issuer check when set |
| `DEV_JWT_HMAC_SECRET` | dev only | enables HS256 decoder and the dev token endpoint (profile `dev`) |
| `INTERNAL_API_TOKEN` | yes | shared secret for `/internal/**` and outgoing internal calls |
| `MERCHANT_TOKEN_SECRET` | yes | HS256 key for client-credentials tokens (>= 32 bytes) |
| `WEBHOOK_ENCRYPTION_KEY` | yes | base64 32 bytes |
| `PARCEL_SERVICE_URL`, `WEBHOOK_SERVICE_URL` | no | defaults `http://parcel:8080`, `http://webhook:8080` |
| `PUBLIC_TRACKING_URL` | no | default `http://localhost:4200/track` |
| `WEBHOOK_ALLOW_INSECURE_URLS`, `WEBHOOK_ALLOW_PRIVATE_HOSTS` | no | default `false`, `true` in dev profile |
| `CORS_ALLOWED_ORIGINS` | no | default `http://localhost:4200` (only matters when the portal calls the service directly) |

Dev profile (`application-dev.yml`): enables the dev token endpoint `POST /api/v1/dev/tokens` (`{"sub":"user-1","role":"MERCHANT"}` -> `{"accessToken":"...","expiresIn":3600}` HS256 signed with `DEV_JWT_HMAC_SECRET`) so the service and portal can be exercised before Identity (M2) exists. That controller is `@Profile("dev")` and must not exist in any other profile (test that).

## 10. Frontend: Merchant Portal (Angular)

- Angular 20, standalone components, signals, new control flow, lazy-loaded routes, reactive forms, typed HttpClient, `ChangeDetectionStrategy.OnPush`. Workspace at `frontend/` (`frontend/package.json`, `angular.json`), application project `merchant-portal` under `frontend/projects/merchant-portal`. Other members' apps are added later as extra projects in the same workspace. Karma + Jasmine for tests (CI runs `npm test -- --watch=false --browsers=ChromeHeadless`).
- Responsive, mobile friendly, accessible (labels, focus states, `aria-live` for toasts, colour contrast), no UI library dependency beyond Angular (own small CSS design tokens in `styles.css`, easy to swap for M2's shared design system later).
- API base `/api/v1` (relative). Dev proxy `frontend/proxy.conf.json`: `/api/v1/auth` -> `http://localhost:8080` (Identity/gateway), the rest -> `http://localhost:8083` (merchant-service). Container: nginx serving the build, `/api/` reverse-proxied to `${API_UPSTREAM}` (default `http://merchant:8080`), SPA fallback, security headers, no secrets baked into the image.
- **Auth adapter (ASSUMED, M2)**, isolated in `core/auth/auth-api.service.ts`: `POST /api/v1/auth/login {email,password}` -> `{accessToken, refreshToken, expiresIn, user:{id,email,fullName,role}}`; `POST /api/v1/auth/signup {fullName,email,phone,password,role:"MERCHANT"}` -> same shape; `POST /api/v1/auth/refresh {refreshToken}` -> same. Plus a **dev login** (only when `environment.devLogin` is true) calling `POST /api/v1/dev/tokens`. Token kept in memory + `sessionStorage` (not localStorage), attached by an interceptor, 401 -> try refresh once -> else login page. Route guards by role and merchant status.
- Routes: `/login`, `/signup`, `/onboarding` (register business), `/status` (pending / rejected + resubmit / suspended), `/dashboard`, `/orders`, `/orders/new`, `/orders/bulk`, `/orders/:id`, `/developers/keys`, `/developers/webhooks`, `/developers/docs`, `/profile`. After login: no merchant -> `/onboarding`; not APPROVED -> `/status`; APPROVED -> `/dashboard`. Unapproved merchants cannot reach the order/developer pages (guard) and the API enforces it too.
- Pages: dashboard (stat cards, last-7-days bar chart in plain SVG, recent orders, live/sandbox switch), orders list (filters, search, status badges, pagination, CSV-safe export is **not** required), order detail with timeline, create order form with validation, bulk CSV upload (template download, drag/drop, per-row result table), API keys page (create/rotate/revoke keys and OAuth clients, "shown once" secret dialog with copy button and an explicit acknowledge step), webhook settings (URL, events, active, secret hint, rotate, test with result, delivery log table with `available:false` empty state), API docs page (auth methods, quick start with cURL, idempotency, errors, rate limits, sandbox, webhook signature verification samples, link to `/api/v1/merchant-api/openapi.yaml`), profile page.
- Never render secrets after the one-time dialog; never put tokens in URLs; never log secrets; sanitise by relying on Angular templating (no `innerHTML`).
- Tests: unit tests for services (auth, API clients with `HttpTestingController`), guards, interceptor, the secret-once dialog, key forms validation, CSV result table; at least one component test per major page.

## 11. Things other members need from M3 / must provide

- M1 (gateway): route `/api/v1/merchants/**`, `/api/v1/admin/merchants/**`, `/api/v1/merchant-api/**`, `/api/v1/oauth/token` to `merchant:8080`; do **not** apply Identity-JWT validation to `/merchant-api/**` and `/oauth/token` (merchant-service authenticates those itself); never route `/internal/**`; forward `Authorization`, `X-API-Key`, `Idempotency-Key`, `X-Correlation-Id`; do not rate limit `/merchant-api/**` a second time with a lower limit.
- M2 (identity): issue RS256 JWTs with `sub`, `role` (`MERCHANT` for merchant sign-ups), publish JWKS; signup with role `MERCHANT` must be allowed.
- M4 (parcel): implement `POST /internal/parcels` (section 6), include `merchantId` and `trackingNo` in `parcel.created` / `parcel.status.*` event data; honour `Idempotency-Key`.
- M9: add `merchant.orders.q` bindings, implement `GET /internal/merchants/{merchantId}/webhook-deliveries`, call `GET /internal/merchants/{merchantId}/webhook` on M3 for URL and secret.
- M10: merchant approval UI uses the `/admin/merchants` endpoints in 5.2.
