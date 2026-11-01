# Merchant Service and Merchant Portal: design notes (Member 3)

Design notes for the group design document, Member 3 part. They describe what is built (service in `services/merchant`, portal
in `frontend/projects/merchant-portal`), not what was planned. The binding specification is [`CONTRACT.md`](CONTRACT.md); how to run
and operate the service is in [`services/merchant/README.md`](../../services/merchant/README.md); the public API is described in
[`openapi.yaml`](../../services/merchant/src/main/resources/merchant-api/openapi.yaml).

## 1. Scope and responsibilities

Member 3 owns everything about the **merchant** as a customer of SwiftDrop:

* registration and the admin approval workflow, per-merchant rate limits;
* API keys (hashed, rotation with grace period, revocation) and OAuth 2.0 client-credentials clients;
* the public merchant API (create, get and list orders, ping), the sandbox, and bulk CSV upload for the portal;
* the merchant-side read model of parcel status, kept up to date from parcel events;
* webhook **configuration** (URL, event selection, encrypted secret), a signed test ping and the delivery-log view (delivery itself
  belongs to Member 9);
* the Merchant Portal (Angular): registration, dashboard, orders, API keys, webhook settings, API docs.

Neighbours: Identity (M2) issues the portal JWTs, the Parcel Service (M4) books live parcels, the Webhook Delivery Service (M9)
delivers webhooks and owns the event envelope, the gateway (M1) routes requests, the admin console (M10) uses the approval endpoints.

## 2. Merchant use case overview

```mermaid
flowchart LR
    MU["Merchant user<br/>(portal)"]
    INT["Merchant backend<br/>(API client)"]
    ADM["Administrator<br/>(admin console)"]

    subgraph SYS["Merchant Service and Merchant Portal"]
        direction TB
        UC1(["Register business and resubmit"])
        UC2(["Manage profile"])
        UC3(["Create and rotate API keys"])
        UC4(["Create OAuth clients"])
        UC5(["Place order in portal, live or sandbox"])
        UC6(["Upload orders as CSV"])
        UC7(["Track orders and view dashboard"])
        UC8(["Configure webhook and send test"])
        UC9(["View webhook delivery log"])
        UC10(["Get access token"])
        UC11(["Create order via API"])
        UC12(["Read orders via API"])
        UC13(["Advance sandbox order"])
        UC14(["Approve, reject, suspend, reinstate"])
        UC15(["Set rate limit"])
        UC16(["Receive parcel status events"])
        UC17(["Provide webhook config and merchant identity"])
    end

    subgraph EXT["Other SwiftDrop services"]
        direction TB
        M4["Parcel Service M4"]
        M9["Webhook Delivery Service M9"]
    end

    MU --- UC1
    MU --- UC2
    MU --- UC3
    MU --- UC4
    MU --- UC5
    MU --- UC6
    MU --- UC7
    MU --- UC8
    MU --- UC9
    INT --- UC10
    INT --- UC11
    INT --- UC12
    INT --- UC13
    ADM --- UC14
    ADM --- UC15
    UC5 -.->|"books parcel"| M4
    UC11 -.->|"books parcel"| M4
    UC16 -.->|"consumes parcel events from"| M4
    UC9 -.->|"asks"| M9
    UC17 -.->|"serves"| M9
```

Creating credentials and orders requires an approved merchant (the approval use case, section 3); the merchant API enforces it on every call. Portal users sign in with a JWT issued by the Identity Service (M2), which is not drawn.

## 3. Merchant approval

### 3.1 Activity diagram

```mermaid
flowchart TD
    A([Start]) --> B["Merchant user signs up at Identity<br/>role MERCHANT"]
    B --> C["Merchant registers business<br/>POST /merchants/register"]
    C --> D["Merchant saved as PENDING<br/>outbox: merchant.registered"]
    D --> E["Administrator opens pending merchants<br/>GET /admin/merchants?status=PENDING"]
    E --> F{"Decision"}
    F -- "approve" --> G["Status APPROVED<br/>decided_by, decided_at set<br/>outbox: merchant.approved"]
    F -- "reject with reason" --> H["Status REJECTED<br/>reason stored<br/>outbox: merchant.rejected"]
    H --> I["Merchant reads reason in portal status page"]
    I --> J["Merchant edits details<br/>PUT /merchants/me"]
    J --> K["POST /merchants/me/resubmit<br/>status PENDING, decision cleared<br/>outbox: merchant.registered"]
    K --> E
    G --> L["Merchant can create API keys,<br/>OAuth clients and orders"]
    L --> M{"Misuse or request<br/>to block?"}
    M -- "no" --> L
    M -- "yes: suspend with reason" --> N["Status SUSPENDED<br/>all keys and tokens stop working<br/>on the next request"]
    N --> O{"Resolved?"}
    O -- "yes: reinstate" --> G
    O -- "no" --> N
```

### 3.2 States

```mermaid
stateDiagram-v2
    [*] --> PENDING : register
    PENDING --> APPROVED : approve
    PENDING --> REJECTED : reject with reason
    REJECTED --> PENDING : resubmit
    REJECTED --> APPROVED : approve
    APPROVED --> SUSPENDED : suspend with reason
    SUSPENDED --> APPROVED : reinstate
    SUSPENDED --> APPROVED : approve
```

The transition rules live in the `Merchant` entity (`approve`, `reject`, `suspend`, `reinstate`, `resubmit` call `requireStatus`),
so no caller can bypass them; an illegal transition is `409 INVALID_STATE`. Only `APPROVED` merchants pass the check in
`MerchantApiAuthenticator`, `CurrentMerchantProvider.requireApprovedMerchant()` and `OrderService`.

## 4. Order creation through the merchant API

```mermaid
sequenceDiagram
    autonumber
    participant C as Merchant backend
    participant GW as API Gateway M1
    participant AF as MerchantApiAuthFilter
    participant RL as RateLimiter
    participant OC as MerchantApiOrderController
    participant OS as OrderService
    participant DB as merchant_db
    participant PS as Parcel Service M4
    participant MQ as RabbitMQ

    C->>GW: POST /api/v1/merchant-api/orders (X-API-Key, Idempotency-Key)
    GW->>AF: forward (no JWT check on this path)
    AF->>DB: load key by prefix, compare SHA-256, load merchant
    alt key unknown, revoked or expired
        AF-->>C: 401 INVALID_API_KEY
    else merchant not APPROVED
        AF-->>C: 403 MERCHANT_NOT_APPROVED
    end
    AF->>RL: tryAcquire(merchantId, limit per minute)
    alt bucket empty
        AF-->>C: 429 RATE_LIMITED (Retry-After)
    end
    AF->>OC: principal (merchant, environment, scopes)
    Note over AF,OC: authorization by scope: POST needs orders:write
    OC->>OS: create(NewOrder)
    OS->>DB: re-read merchant, must be APPROVED
    OS->>DB: find order by idempotency key and externalRef
    alt same key, same request hash
        OS-->>C: 200 stored order + Idempotent-Replayed true
    else same key, different request
        OS-->>C: 422 IDEMPOTENCY_KEY_REUSED
    else first request still PENDING
        OS-->>C: 409 IDEMPOTENCY_KEY_IN_USE (Retry-After 1)
    else externalRef used by another order
        OS-->>C: 409 DUPLICATE_EXTERNAL_REF
    end
    OS->>DB: insert PENDING placeholder, commit (unique constraints decide races)
    alt TEST credential (sandbox)
        OS->>OS: SandboxSimulator: SBX tracking number and fee
    else LIVE credential
        OS->>PS: POST /internal/parcels (no DB transaction open)
        alt 4xx refusal
            PS-->>OS: error
            OS->>DB: delete placeholder
            OS-->>C: 422 PARCEL_REJECTED
        else timeout or 5xx
            PS-->>OS: error
            Note over OS,DB: placeholder stays PENDING: M4 may have booked the parcel
            OS-->>C: 503 PARCEL_SERVICE_UNAVAILABLE
        else booked
            PS-->>OS: 201 parcelId, trackingNo, fee
            PS->>PS: save parcel and its outbox row in one transaction
        end
    end
    OS->>DB: complete order (parcel_id, tracking_no, status CREATED, fee)
    OS-->>C: 201 order + Location header
    Note over PS,MQ: later and asynchronous
    PS->>MQ: outbox relay publishes parcel.created, parcel.status.*
    MQ->>OS: merchant.orders.q to ParcelEventConsumer
    OS->>DB: processed_events, order_status_history, update status
```

The merchant service itself writes no outbox row for an order: its outbox carries only `merchant.*` events. The event that tells the
world about a new parcel (`parcel.created`) is written to the **Parcel Service's** outbox in the same transaction as the parcel;
the merchant service learns about status changes through its queue.

## 5. API key and OAuth client-credentials authentication

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant GW as API Gateway
    participant TC as OAuthTokenController
    participant TS as OAuthTokenService
    participant AF as MerchantApiAuthFilter
    participant AU as MerchantApiAuthenticator
    participant DB as merchant_db

    Note over C,DB: A. API key (header X-API-Key)
    C->>GW: GET /merchant-api/ping with X-API-Key sd_test_ab12cd34_secret
    GW->>AF: forward
    AF->>AU: authenticateApiKey(rawKey)
    AU->>DB: select by key_prefix sd_test_ab12cd34
    AU->>AU: SHA-256(rawKey) equals key_hash (constant time), ACTIVE, not expired
    AU->>DB: load merchant, must be APPROVED
    AU->>DB: touch last_used_at (at most once a minute)
    AU-->>AF: principal (merchantId, TEST, both scopes)
    AF-->>C: 200 with X-RateLimit headers

    Note over C,DB: B. Client credentials (RFC 6749 section 4.4)
    C->>GW: POST /oauth/token Basic client_id:secret, grant_type=client_credentials
    GW->>TC: forward
    TC->>TS: issueToken(credentials, grantType, scope)
    TS->>DB: select client by client_id, compare SHA-256 of secret, ACTIVE
    TS->>DB: merchant must be APPROVED
    alt any failure
        TS-->>C: 401 invalid_client (same answer for every cause)
    else wrong grant_type or scope too wide
        TS-->>C: 400 unsupported_grant_type or invalid_scope
    end
    TS->>TS: sign JWT HS256 (sub, merchantId, credentialId, env, scope, exp, jti)
    TS-->>C: 200 access_token, Bearer, expires_in 3600

    C->>GW: GET /merchant-api/orders with Authorization Bearer access_token
    GW->>AF: forward
    AF->>AU: authenticateBearer(token)
    AU->>AU: verify signature, issuer and expiry
    AU->>DB: client still ACTIVE and unchanged, merchant APPROVED
    AU-->>AF: principal with the scopes from the token
    AF-->>C: 200 or 403 FORBIDDEN when the scope is missing
```

Every request re-reads the credential and the merchant. That makes revocation and suspension immediate, at the price of one or two
indexed lookups per call.

## 6. Webhook configuration and delivery

```mermaid
sequenceDiagram
    autonumber
    participant U as Merchant user (portal)
    participant WS as WebhookService (M3)
    participant UV as UrlSafetyValidator
    participant DB as merchant_db
    participant MQ as RabbitMQ
    participant M9 as Webhook Delivery Service (M9)
    participant EP as Merchant endpoint

    Note over U,DB: Configuration (Member 3)
    U->>WS: PUT /merchants/me/webhook url, events, active
    WS->>UV: validate url (https, no userinfo, resolved addresses public)
    alt rejected
        WS-->>U: 422 WEBHOOK_URL_REJECTED
    end
    WS->>WS: first save: generate whsec_ secret, encrypt with AES-256-GCM
    WS->>DB: upsert webhook_configs (ciphertext, secret_hint)
    WS-->>U: 200 configuration (secret shown once)
    U->>WS: POST /merchants/me/webhook/test
    WS->>UV: validate again (DNS may have changed)
    WS->>EP: signed webhook.test, 5 s timeout, no redirects
    EP-->>WS: 2xx
    WS->>DB: store last_test_at and last_test_status only
    WS-->>U: delivered, httpStatus, durationMs

    Note over MQ,EP: Delivery (Member 9)
    MQ->>M9: parcel.status.delivered from webhook.q
    M9->>WS: GET /internal/merchants/{id}/webhook (X-Internal-Token)
    WS->>DB: read config
    WS-->>M9: url, decrypted secret, active, events
    M9->>M9: skip when inactive or event not selected, build payload, timestamp
    M9->>M9: signature = HMAC-SHA256(secret, timestamp + "." + body)
    M9->>EP: POST with X-SwiftDrop-Event-Id, -Event-Type, -Timestamp, -Signature
    EP->>EP: reject old timestamp, verify signature, skip known event id
    alt 2xx within 5 s
        EP-->>M9: 200
        M9->>M9: delivery DELIVERED, ack message
    else timeout, connection error, 5xx or 429
        EP-->>M9: error
        loop attempts 2 to 6 after 5 s, 30 s, 2 min, 10 min, 30 min
            M9->>MQ: publish to webhook.delay queue (TTL), back to webhook.q
            M9->>EP: POST again, same event id, new timestamp and signature
        end
        M9->>MQ: still failing: webhook.dlq and event webhook.delivery.exhausted
    else other 4xx
        EP-->>M9: 400, 401 or 404
        M9->>M9: delivery FAILED at once, no retry
    end
    U->>WS: GET /merchants/me/webhook/deliveries
    WS->>M9: GET /internal/merchants/{id}/webhook-deliveries
    M9-->>WS: page of attempts
    WS-->>U: content, or available false when M9 cannot be reached
```

## 7. Data model (merchant side)

Source: `src/main/resources/db/migration/V1__init.sql` (PostgreSQL 16; the same script runs on H2 in the tests). Columns that only
repeat a pattern (pickup and drop-off address columns, the other audit timestamps) are left out of the picture.

```mermaid
erDiagram
    merchants ||--o{ api_keys : "issues"
    merchants ||--o{ oauth_clients : "issues"
    merchants ||--o| webhook_configs : "has one"
    merchants ||--o{ merchant_orders : "places"
    merchant_orders ||--o{ order_status_history : "timeline"
    processed_events ||..o{ order_status_history : "same event_id (no foreign key)"

    merchants {
        uuid id PK
        varchar owner_user_id UK "Identity user id (jwt sub)"
        varchar business_name
        varchar contact_email
        varchar contact_phone
        varchar status "PENDING APPROVED REJECTED SUSPENDED"
        varchar decided_by
        timestamptz decided_at
        varchar decision_reason
        int rate_limit_per_minute "1 to 100000, default 60"
        bigint version "optimistic lock"
    }
    api_keys {
        uuid id PK
        uuid merchant_id FK
        varchar label
        varchar environment "LIVE or TEST"
        varchar key_prefix UK "public, sd_live_ab12cd34"
        varchar key_hash "SHA-256 of the whole key"
        varchar status "ACTIVE or REVOKED"
        timestamptz expires_at "grace period after rotation"
        timestamptz last_used_at
        uuid rotated_from_id "previous key, no foreign key"
    }
    oauth_clients {
        uuid id PK
        uuid merchant_id FK
        varchar client_id UK "mc_ plus 20 characters"
        varchar secret_hash "SHA-256"
        varchar environment "LIVE or TEST"
        varchar scopes "orders:write orders:read"
        varchar status "ACTIVE or REVOKED"
        timestamptz last_used_at
    }
    webhook_configs {
        uuid merchant_id PK, FK
        varchar url
        varchar secret_ciphertext "AES-256-GCM, base64 of iv and ciphertext"
        varchar secret_hint "first 10 characters"
        boolean active
        varchar events "star or comma separated types"
        timestamptz last_test_at
        varchar last_test_status
    }
    merchant_orders {
        uuid id PK
        uuid merchant_id FK
        varchar external_ref UK "unique per merchant"
        varchar idempotency_key UK "unique per merchant"
        varchar request_hash "canonical request fingerprint"
        varchar parcel_id
        varchar tracking_no UK
        varchar status "PENDING CREATED ASSIGNED and so on"
        boolean sandbox
        varchar source "API PORTAL BULK"
        varchar recipient_name
        varchar recipient_phone
        numeric weight_kg
        varchar size "SMALL MEDIUM LARGE"
        numeric fee_amount
        varchar fee_currency
        varchar failure_reason
        timestamptz last_event_at "newest applied parcel event"
        bigint version "optimistic lock"
    }
    order_status_history {
        uuid id PK
        uuid order_id FK
        varchar event_id UK "unique with order_id"
        varchar from_status
        varchar to_status
        timestamptz occurred_at
        varchar note
    }
    outbox_events {
        uuid id PK
        varchar event_id UK
        varchar event_type
        varchar aggregate_id
        varchar payload "JSON envelope, max 4000"
        timestamptz created_at
        timestamptz published_at "null until confirmed"
        int attempts
        varchar last_error
    }
    processed_events {
        varchar event_id PK
        varchar event_type
        timestamptz processed_at
    }
```

`outbox_events` and `processed_events` have no foreign keys on purpose: the first is a queue of messages leaving the service, the second a
set of ids of messages that arrived. The unique constraints do the real work: `(merchant_id, idempotency_key)` and
`(merchant_id, external_ref)` decide concurrent duplicate orders, `(order_id, event_id)` stops a duplicate history row,
`processed_events.event_id` makes the consumer idempotent, `key_prefix` makes key lookup a single indexed read.

## 8. Security chains

```mermaid
flowchart TD
    R["HTTP request"] --> CF["CorrelationIdFilter<br/>sanitise or generate X-Correlation-Id, set MDC"]
    CF --> P{"Path"}
    P -- "/internal/**" --> C0["Chain 0<br/>InternalTokenFilter<br/>X-Internal-Token, constant time"]
    P -- "/api/v1/merchant-api/** and /api/v1/oauth/**" --> C1["Chain 1<br/>MerchantApiAuthFilter<br/>API key or bearer token,<br/>approval check, rate limit"]
    P -- "everything else" --> C2["Chain 2<br/>OAuth2 resource server<br/>Identity JWT, JwtRoleConverter"]
    C0 --> A0{"ROLE_INTERNAL?"}
    C1 --> A1{"scope orders:read<br/>or orders:write?"}
    C2 --> A2{"MERCHANT for /merchants/**<br/>ADMIN for /admin/**"}
    A0 -- "yes" --> CT["Controller"]
    A1 -- "yes" --> CT
    A2 -- "yes" --> CT
    A0 -- "no" --> E["JSON error 401 or 403<br/>same ApiError shape"]
    A1 -- "no" --> E
    A2 -- "no" --> E
```

## 9. Design decisions and patterns

### 9.1 Patterns applied

| Pattern | Where | Why it is there |
|---|---|---|
| **Strategy** (behind an interface) | `RateLimiter` with `InMemoryRateLimiter`; `ParcelClient` with `HttpParcelClient` (tests use a fake); `HostResolver` with `DnsHostResolver` | The algorithm can be replaced without touching callers: a Redis limiter for several replicas, a different parcel API, a fake DNS in tests |
| **Repository** and **Specification** | Spring Data repositories (`MerchantRepository`, `MerchantOrderRepository`, `ApiKeyRepository`, ...); `OrderSpecifications`, `MerchantSpecifications` | Persistence is hidden behind interfaces; the combinable list filters (status, text search, sandbox mode, date range) are built from small specifications that are always scoped to one merchant |
| **Transactional Outbox** | `OutboxService.enqueue` (`Propagation.MANDATORY`), `OutboxRelay`, table `outbox_events` | A merchant change and its event are committed together, so an event is neither lost when the broker is down nor sent for a rolled-back change |
| **Idempotent Consumer** | `ParcelEventHandler` with `ProcessedEventRepository.insertIfAbsent`; unique `(order_id, event_id)` | RabbitMQ delivers at least once; duplicates and redeliveries change nothing |
| **Idempotent API (request fingerprint)** | `RequestFingerprint`, `Idempotency-Key`, placeholder row in `OrderService` | A client may retry a create safely; the database unique constraints, not application locks, arbitrate concurrent duplicates |
| **Template Method** | `MerchantApiAuthFilter`, `InternalTokenFilter`, `CorrelationIdFilter` extend `OncePerRequestFilter` (`doFilterInternal`, `shouldNotFilter`); `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` and overrides its hooks | The framework fixes the skeleton (once per request, which exception maps where); the service fills in only the variable steps |
| **Adapter** | `HttpParcelClient` (assumed M4 HTTP contract to the `ParcelClient` port), `DeliveryLogClient` (M9), `MerchantApiAuthentication` (principal to Spring's `Authentication`), `JwtRoleConverter` (claims to authorities) | Foreign shapes stay in one class; the rest of the code speaks the service's own types |
| **Chain of Responsibility / Decorator-style filters** | `CorrelationIdFilter` then `MerchantApiAuthFilter` then the authorization filter; the retry interceptor around the AMQP listener (`OrderMessagingConfig.retryInterceptor`) | Each wrapper adds one concern (correlation id, authentication and rate limit headers, retries) around the next without changing it |
| **State** (rules next to the data) | `Merchant` transitions (`requireStatus`); `OrderStatus.nextInSandboxLifecycle`, `isTerminal` | Legal transitions are defined once, on the type that has the state |
| **Factory** | `ApiErrorFactory`, `KeyGenerator`, named constructors such as `Merchant.register`, `MerchantOrder.placeholder`, `OAuthException.invalidClient` | Creation rules (ids, timestamps, error shape) in one place |
| **Facade** | `OrderService.create` | Controllers (portal, API, bulk importer) call one method; idempotency, sandbox versus live and cleanup are not their concern |
| **Value objects** | Java records for every DTO, `Money`, `ApiError`, `RateLimitResult`; sealed interface `WebhookView` (`NotConfigured` or `Configured`) | Immutable, normalised in the compact constructor (trim, phone clean-up), logging-safe `toString` |

### 9.2 SOLID, with real classes

* **Single responsibility.** `OrderService` decides what to do, `OrderWriter` owns the short database transactions, `RequestFingerprint`
  hashes requests, `SandboxSimulator` makes up tracking numbers and fees, `HttpParcelClient` talks HTTP. `WebhookSigner` only signs and verifies;
  `UrlSafetyValidator` only judges URLs.
* **Open/closed.** `MerchantApiSecurityConfig` authorizes by HTTP method and scope, not by listing endpoints: a new `GET` under
  `/merchant-api/` is protected by `orders:read` with no configuration change. A new error is one `ErrorCode` constant with its status;
  `GlobalExceptionHandler` needs no change.
* **Liskov substitution.** Any `RateLimiter` or `ParcelClient` can stand in for another; every `ApiException` subclass is handled uniformly by the
  one handler through its status and code.
* **Interface segregation.** `ParcelClient` has a single method, `HostResolver` one, `RateLimiter` one; collaborators depend only on what they call.
* **Dependency inversion.** `OrderService` is constructed with `ParcelClient`, `MerchantService`, `Clock` and `OrderWriter`; no `new HttpClient()` in
  business code, and the `Clock` bean lets tests control time (token expiry, rate limit refill, key grace period).

### 9.3 Security decisions

| Decision | Reason |
|---|---|
| API keys are `sd_<env>_<id8>_<secret43>`; only the SHA-256 of the whole key is stored; the public prefix is the lookup key; shown once | A database leak yields no usable keys. The secret is 256 random bits, so a fast hash is adequate (no password-style stretching needed) and lookups stay cheap; hashes are compared with `MessageDigest.isEqual` |
| Separate filter chain for `/merchant-api` and `/oauth`, independent of Identity | Integrators use keys, not user sessions. A bug in one chain cannot weaken the other; the gateway must not apply user-JWT checks there |
| Credential and merchant are loaded on **every** request | Revoking a key, deleting a client or suspending a merchant takes effect at once; no cache to invalidate |
| One 401 message for every credential failure; `invalid_client` for every token failure, including a merchant that is not approved | The API cannot be used to learn which keys, clients or merchants exist |
| Rejected credentials do not consume the rate limit and get no rate-limit headers | Garbage requests cannot be used to exhaust a merchant's budget; headers are not an oracle |
| Token endpoint refuses parameters in the query string; client authentication before `grant_type` is looked at | Secrets must not reach access logs (RFC 6749 section 3.2); an unauthenticated caller learns nothing about supported grants |
| Access tokens are HS256 JWTs signed with `MERCHANT_TOKEN_SECRET`, validated only by this service, scopes inside the token, client re-checked on use | No JWKS or key distribution for a token that no other service accepts; revoking the client kills tokens already issued |
| Webhook secrets are encrypted (AES-256-GCM, random IV), not hashed | They must be recoverable to sign deliveries; GCM authenticates the ciphertext, the key lives in an environment variable |
| Webhook signature `HMAC-SHA256(secret, timestamp + "." + body)` and a 5-minute window | Proves origin and integrity, makes a replayed request detectable; the event id enables duplicate detection |
| Webhook URL checks: https only, no credentials, **resolved** addresses must be public, re-checked before each ping, no redirects, response body discarded, only fixed error phrases returned | Server-side request forgery protection; judging resolved addresses defeats numeric host tricks (`2130706433`, `0x7f.1`) |
| Errors never echo input; SQL, stack traces and upstream bodies are not exposed; 500 has a generic message with the correlation id | No information leak; support can still find the log entry |
| PII is masked or left out of logs (`PiiMasker`, custom `toString` on DTOs); a log-leak test runs the flows at DEBUG | Spring logs bodies at DEBUG; a mistaken log level must not leak personal data or secrets |
| Correlation ids are accepted only as 1 to 64 characters of `[A-Za-z0-9._:-]` | Prevents log injection and oversized values travelling into events |
| `@Profile("dev")` token endpoint; the HS256 decoder works only in the `dev` and `test` profiles | A leaked development secret cannot weaken a production deployment |
| `/internal/**` on its own chain with a constant-time token comparison; never routed through the gateway | Service-to-service secrets are not user credentials |
| Optimistic locking (`@Version`) on `merchants` and `merchant_orders`; CSV cells starting with `=`, `+`, `-`, `@` stored as they are and escaped on export | Lost updates become `409 OPTIMISTIC_LOCK`; spreadsheet injection is prevented where data leaves the system |
| TEST and LIVE credentials see disjoint order sets; sandbox orders publish no events and trigger no webhooks | An integration test can never touch real parcels or notify real customers |

### 9.4 Other design decisions

* **No database transaction around the parcel call.** The placeholder is committed first, then the HTTP call runs, then the row is completed.
  A slow Parcel Service cannot hold connections, and the unique constraints make concurrent duplicates safe. The price is a short-lived `PENDING` row
  (cleaned up after five minutes if the process dies in between).
* **Read model instead of calling M4 for reads.** Dashboards read `merchant_orders` and `order_status_history`, fed by events. Reads stay fast and survive
  an M4 outage; status can lag by the delivery time of the event.
* **Late events are recorded, not applied.** `last_event_at` guards the status: an event older than the last applied one goes into the history only.
* **Assumptions are isolated.** Everything guessed about M4, M9 and M2 sits in `HttpParcelClient`, `DeliveryLogClient`, `WebhookSigner`,
  `JwtDecoderConfig` and `JwtRoleConverter`, so a change of the real interface touches one class.

### 9.5 Trade-offs and known limitations

| Topic | Limitation | Possible improvement |
|---|---|---|
| Rate limiting | The token bucket is in memory per replica, so N replicas allow up to N times the limit | A Redis-backed `RateLimiter`; the interface already exists |
| Outbox relay | No row locking: several replicas can publish the same row twice, and delivery order across replicas is not guaranteed; polling adds up to about a second of latency | `select ... for update skip locked`, or a single relay replica |
| Booking | Exactly-once only if M4 honours its `Idempotency-Key`. The key is the placeholder's id, and an ambiguous failure keeps the placeholder, so the retry (same key, or same request and `externalRef`) completes that placeholder and M4 returns the original booking | Reconcile placeholders against M4 instead of trusting its de-duplication |
| Early events | A status event that arrives before the order is completed matches no order and is recorded as processed (a window of milliseconds) | Park unmatched events and retry them |
| Event inputs | The consumer needs `data.merchantId` (UUID) and `data.trackingNo` or `data.parcelId`. `parcel.assigned` and `parcel.cancelled` carry only a parcel id in the M9 catalogue, so they are matched by parcel id; the M9 example uses `MER-031` as merchant id, which is not a UUID | Agree the field and the id format with M4, Dispatch and M9 |
| Webhooks | Delivery log and delivery behaviour are as designed by M9; this service never saw the real M9. SSRF check has a small DNS-rebinding window | Pin the validated IP; apply the same checks in M9; egress rules |
| Authentication cost | Two or three indexed lookups on every merchant API call | A short-lived cache, accepting slower revocation |
| Tokens | No refresh tokens; HS256 shared secret known to this service only; one secret for all tokens (rotation invalidates them all) | Key id (`kid`) and a key ring |
| Database tests | The integration tests ran on H2 in PostgreSQL mode; locks and PostgreSQL specific SQL were not exercised on a real PostgreSQL, the broker test needs Docker | Run the suite in CI with Testcontainers PostgreSQL |
| Bulk upload | Sequential, so a down Parcel Service makes a large live file slow; rows are never skipped early | Asynchronous import job with progress |
| Dev conveniences | The production bundle of the portal still contains the unreachable dev-login code behind a build flag | File replacement in the build |

## 10. How it is tested (summary)

* JUnit 5, Mockito, MockMvc and AssertJ on H2 (PostgreSQL mode) with the real Flyway migration; no Docker needed; the whole application context is
  started for the API, authorization and workflow tests (merchant approval, API key validation, unapproved merchants blocked from creating orders).
* Event flow: relay against a mocked `RabbitTemplate` and the real outbox table, consumer against the real handler (duplicates, late events, poison
  messages, retry then dead-letter), plus a broker test with Testcontainers that is skipped without Docker.
* Concurrency tests: eight threads with the same idempotency key or `externalRef`, four threads delivering one event.
* Security tests: log-leak tests at DEBUG for secrets and personal data, internal endpoint authorization, dev endpoint absent outside the dev profile.
* `OpenApiParityTest` keeps `openapi.yaml` and the controllers in step. JaCoCo enforces at least 70% line coverage in `mvn verify` (about 98% at the last full run).
* Portal: Karma and Jasmine (services with `HttpTestingController`, guards, interceptor, secret-once dialog, one component test per page).
