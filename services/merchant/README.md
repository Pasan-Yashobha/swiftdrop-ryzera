# Merchant Service

Spring Boot service of the SwiftDrop platform that owns **merchants**: onboarding with admin approval, API
credentials, orders placed through the merchant API and portal, and webhook settings. The full specification is
[`docs/merchant-service/CONTRACT.md`](../../docs/merchant-service/CONTRACT.md); this README only covers running and
testing the service.

| | |
|---|---|
| Stack | Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, Spring Security (OAuth2 resource server), Spring AMQP |
| Database | PostgreSQL `merchant_db` (user `merchant_svc`), schema in `src/main/resources/db/migration` |
| Messaging | RabbitMQ, exchange `swiftdrop.events`, transactional outbox |
| Ports | `8080` in the container, `8083` on the host |
| Health / metrics | `/actuator/health`, `/actuator/prometheus` |

## What is in this module

| Package | Responsibility |
|---|---|
| `common` | error format (`ApiError`, `ApiException` family, `GlobalExceptionHandler`), correlation ids, `PiiMasker`, paging |
| `config` | `AppProperties` (all settings), security filter chains, JWT decoder, RabbitMQ exchanges, clock, scheduling |
| `security` | role mapping of Identity JWTs, `CurrentUser`, internal token filter, JSON 401/403, dev token endpoint |
| `event` | event envelope, transactional outbox and the relay that publishes it to RabbitMQ |
| `merchant` | registration, approval workflow, admin management |

## Run locally

Prerequisites: JDK 21 (JDK 17 also works with `-Djava.version=17`), Maven, and Postgres and RabbitMQ from the root
compose file.

```bash
# from the repository root
docker compose up -d postgres rabbitmq

# from services/merchant
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile (`application-dev.yml`) points at `localhost` and fills in development-only secrets, so no
environment variables are needed. The service then listens on `http://localhost:8080`.
Change the port with `--server.port=8083` if something else uses 8080.

### As a container

```bash
docker build -t swiftdrop/merchant services/merchant
docker run --rm -p 8083:8080 --network swiftdrop \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/merchant_db \
  -e SPRING_DATASOURCE_USERNAME=merchant_svc -e SPRING_DATASOURCE_PASSWORD=... \
  -e SPRING_RABBITMQ_HOST=rabbitmq -e SPRING_RABBITMQ_USERNAME=swiftdrop -e SPRING_RABBITMQ_PASSWORD=... \
  -e IDENTITY_JWKS_URI=http://identity:8080/.well-known/jwks.json \
  -e INTERNAL_API_TOKEN=... -e MERCHANT_TOKEN_SECRET=... -e WEBHOOK_ENCRYPTION_KEY=... \
  swiftdrop/merchant
```

The image is built from this folder, runs as a non-root user and has a health check on `/actuator/health`.

## Configuration

All settings come from environment variables; `application.yml` contains no secrets. The service **refuses to start**
with a message naming the variable when a required one is missing or malformed.

| Variable | Required | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | yes | PostgreSQL, e.g. `jdbc:postgresql://postgres:5432/merchant_db` |
| `SPRING_RABBITMQ_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD` | yes | broker |
| `IDENTITY_JWKS_URI` | production | JWKS of the Identity service (RS256 tokens) |
| `JWT_ISSUER` | no | when set, the `iss` claim of Identity tokens must match |
| `INTERNAL_API_TOKEN` | yes | shared secret for `/internal/**` and calls to other services |
| `MERCHANT_TOKEN_SECRET` | yes | signs client-credentials access tokens, at least 32 bytes |
| `WEBHOOK_ENCRYPTION_KEY` | yes | base64 of exactly 32 bytes (AES-256 key for webhook secrets) |
| `DEV_JWT_HMAC_SECRET` | dev only | HS256 key of the development tokens, at least 32 bytes; ignored outside the `dev`/`test` profiles |
| `PARCEL_SERVICE_URL`, `WEBHOOK_SERVICE_URL` | no | default `http://parcel:8080`, `http://webhook:8080` |
| `PUBLIC_TRACKING_URL` | no | default `http://localhost:4200/track` |
| `WEBHOOK_ALLOW_INSECURE_URLS`, `WEBHOOK_ALLOW_PRIVATE_HOSTS` | no | default `false` (private hosts `true` in `dev`) |
| `CORS_ALLOWED_ORIGINS` | no | comma separated, default `http://localhost:4200` |

Generate a webhook key with `openssl rand -base64 32`. Finer settings (outbox polling, bulk upload limits,
timeouts) live under the `swiftdrop.*` prefix, see `config/AppProperties`.

## Endpoints

All paths are under `/api/v1`. Errors always use the JSON shape of CONTRACT section 4 and carry the
`X-Correlation-Id` of the request.

| Method and path | Role | Purpose |
|---|---|---|
| `POST /merchants/register` | MERCHANT | register the caller's business (status `PENDING`) |
| `GET /merchants/me`, `PUT /merchants/me` | MERCHANT | read / edit the caller's own merchant |
| `POST /merchants/me/resubmit` | MERCHANT | ask for a new review after a rejection |
| `GET /admin/merchants?status=&search=&page=&size=` | ADMIN | list and search merchants |
| `GET /admin/merchants/{id}` | ADMIN | one merchant |
| `POST /admin/merchants/{id}/approve`, `/reject`, `/suspend`, `/reinstate` | ADMIN | review workflow (`reject` and `suspend` need `{"reason": "..."}`) |
| `PUT /admin/merchants/{id}/rate-limit` | ADMIN | `{"requestsPerMinute": 1..100000}` |
| `POST /dev/tokens` | none, `dev` profile only | issue a development token |
| `/internal/**` | `X-Internal-Token` | service to service calls (webhook module) |

Credentials, orders and webhook endpoints are added by their own modules (see the contract, sections 5.3 to 5.6).

Events written to the outbox in the same transaction as the change: `merchant.registered` (also on resubmit),
`merchant.approved`, `merchant.rejected`. They carry identifiers only.

## Development tokens (profile `dev`)

Until the Identity service exists, `POST /api/v1/dev/tokens` signs an HS256 token with `DEV_JWT_HMAC_SECRET`.
The endpoint exists only in the `dev` profile.

```bash
# a merchant user
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/dev/tokens \
  -H 'Content-Type: application/json' \
  -d '{"sub":"user-1","role":"MERCHANT"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s -X POST http://localhost:8080/api/v1/merchants/register \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"businessName":"Lanka Crafts","contactEmail":"owner@lankacrafts.lk","contactPhone":"+94771234567",
       "addressLine":"12 Galle Road","city":"Colombo"}'

# an administrator approves it
ADMIN=$(curl -s -X POST http://localhost:8080/api/v1/dev/tokens \
  -H 'Content-Type: application/json' \
  -d '{"sub":"admin-1","role":"ADMIN"}' | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')

curl -s http://localhost:8080/api/v1/admin/merchants?status=PENDING -H "Authorization: Bearer $ADMIN"
curl -s -X POST http://localhost:8080/api/v1/admin/merchants/<merchant-id>/approve -H "Authorization: Bearer $ADMIN"
```

Roles are `MERCHANT`, `ADMIN`, `DISPATCHER`, `CUSTOMER` and `COURIER`; the token lives for one hour.

## Tests

```bash
mvn verify      # unit and integration tests, JaCoCo report and the coverage gate
```

* No Docker is needed: integration tests run the whole application on H2 in PostgreSQL mode with the real Flyway
  migration. There is no broker; the outbox relay is tested against a mocked `RabbitTemplate`.
* The build fails when line coverage drops below `jacoco.minimum` (default `0.70`, raise it with
  `-Djacoco.minimum=0.8`). The report is written to `target/site/jacoco/index.html`.
* On a machine with JDK 17 only, add `-Djava.version=17`.
* Test helpers for the other modules are in `src/test/java/lk/swiftdrop/merchant/testsupport`:
  `AbstractIntegrationTest` (Spring Boot test with MockMvc and a clean database per test), `TestAuth`
  (`merchant(sub)`, `admin(sub)`, `customer(sub)`, `internal()`), `MerchantFixtures` (persist a merchant in any
  status) and `TestProperties` (an `AppProperties` for unit tests).
