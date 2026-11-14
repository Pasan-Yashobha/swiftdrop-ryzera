# Gateway service (Member 1)

The single entry point of SwiftDrop. Spring Cloud Gateway (reactive), Java 21, port **8080**.
All Angular apps and merchant systems call `http://localhost:8080/api/v1/...`; the gateway checks
the caller and forwards the request to the right service.

## What it does

| Step | Behaviour |
|---|---|
| Correlation ID | Keeps the caller's `X-Correlation-Id` or creates one; sent to the service and returned in the response; one access-log line per request (no headers, tokens or bodies) |
| Authentication | Verifies Identity's RS256 access token with Identity's public keys (`/.well-known/jwks.json`): signature, expiry, issuer. Missing or bad token: `401` |
| Authorization (coarse) | `/api/v1/admin/**` ADMIN (reports: ADMIN or DISPATCHER), `/api/v1/dispatch/**` DISPATCHER or ADMIN, `/api/v1/courier/**` COURIER or ADMIN. Everything else needs any valid token. Wrong role: `403`. **Services still enforce their own fine-grained rules.** |
| Rate limiting | Redis token bucket shared by all gateway instances. `429` when exceeded |
| Routing + circuit breaker | Prefix to service (table below). Service down or breaker open: `503` in the standard error format |
| CORS | Only the Angular origins in `CORS_ALLOWED_ORIGINS` |
| Errors | Same JSON as Identity and Merchant: `timestamp, status, error, code, message, path, correlationId` |

The gateway **passes the `Authorization` header through unchanged**. Identity and Merchant also validate
the token themselves (defense in depth). It does not inject `X-User-*` headers.

### Not authenticated at the gateway (public)
`/api/v1/auth/**`, `/.well-known/**`, `/api/v1/public/**`, and the Merchant machine API
`/api/v1/oauth/token` and `/api/v1/merchant-api/**`. Merchant tokens and API keys are issued and checked by the
Merchant service (they are not Identity tokens). An `Authorization` header on these paths is ignored by the gateway,
so an expired token sent to `/auth/refresh` does not cause a 401.

`/internal/**` is **never** routed or reachable from outside.

Merchant's development-only token endpoint (`/api/v1/dev/**`) is routed and left open **only** when `GATEWAY_DEV_ROUTES=true`
(default `false`; never enable it on AWS).

## Routes

| Path prefix | Service | Rate limit |
|---|---|---|
| `/api/v1/auth/**`, `/.well-known/**` | identity | 10 req/min per IP |
| `/api/v1/users/**`, `/api/v1/admin/users/**` | identity | 10 req/s per user |
| `/api/v1/merchants/**`, `/api/v1/admin/merchants/**` | merchant | 10 req/s per user |
| `/api/v1/merchant-api/**`, `/api/v1/oauth/token` | merchant | 30 req/s per credential |
| `/api/v1/parcels/**` | parcel | per user |
| `/api/v1/quotes/**`, `/api/v1/zones/**`, `/api/v1/admin/pricing/**`, `/api/v1/admin/zones/**` | pricing | per user |
| `/api/v1/dispatch/**` | dispatch | per user |
| `/api/v1/courier/**` | courier | per user |
| `/api/v1/public/tracking/**` | tracking | 5 req/s per IP |
| `/api/v1/tracking/**` (live stream, no breaker) | tracking | per user |
| `/api/v1/admin/audit-logs/**` | audit | per user |
| `/api/v1/admin/notifications/**` | notification | per user |
| `/api/v1/admin/webhooks/**` | webhook | per user |
| `/api/v1/admin/reports/**` | reporting | per user |
| `/api/v1/receipts/**` | document | per user |
| `GET /api/v1/admin/system/health` | handled by the gateway itself (feeds the System Status page, ADMIN only) | - |

Add a service: add one `.route(...)` in `RoutesConfig` and its URI in `application.yml`. Limits are in `application.yml`
(`swiftdrop.gateway.rate-limits`).

## Run it

```bash
docker compose up -d redis                    # Redis is needed for rate limiting
cd services/gateway
mvn spring-boot:run                           # port 8080; expects identity on 8081 and merchant on 8083 (see application.yml)
```

Or in Docker: paste `docker-compose.snippet.yml` into the root `docker-compose.yml`, then
`docker compose up -d --build gateway`.

Try it (Identity must be running):

```bash
curl -i http://localhost:8080/api/v1/users/me                       # 401, standard error body
curl -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{...}"
curl -i http://localhost:8080/api/v1/parcels/1                      # 401 without a token, 503 with a token until Parcel exists
curl -i http://localhost:8080/actuator/health
```

### Point the Angular apps at the gateway
In the frontend dev proxy (`proxy.conf.json`), send `/api` to `http://localhost:8080` instead of `8081`/`8083`.
Optionally set `GOOGLE_REDIRECT_URI` to `http://localhost:8080/api/v1/auth/google/callback`
(and add it in the Google console).

## Settings (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `IDENTITY_URI`, `MERCHANT_URI`, `PARCEL_URI`, ... | `http://localhost:808x` | where each service runs |
| `IDENTITY_JWKS_URI` | `${IDENTITY_URI}/.well-known/jwks.json` | public keys used to verify tokens |
| `GATEWAY_DEV_ROUTES` | `false` | expose Merchant's dev-token endpoint (local development only) |
| `JWT_ISSUER` | `swiftdrop-identity` | expected `iss` claim |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200,http://localhost:4201` | browser origins allowed |
| `SPRING_DATA_REDIS_HOST/PORT/PASSWORD` | `localhost / 6379 / dev_redis_pw` | rate limit store |

## Tests
`mvn verify` runs `GatewayIntegrationTest` (needs Docker: it starts a real Redis). It checks: 401 without token,
public routes, token forwarding and correlation ID, forged/expired/wrong-issuer tokens, role rules (customer vs admin
vs dispatcher), `/internal/**` blocked, 503 when a service is down, 404 format, 429 rate limit, system health.

## Known limits
- A `429` from the rate limiter has an empty body (status only); other errors use the JSON format.
- Rate limiting by IP uses the connection's address. Behind a load balancer the real client IP needs the
  forwarded-header settings of Spring Cloud Gateway (to do for the AWS deployment).
- `/actuator/health` and `/actuator/prometheus` are open on port 8080 for probes and Prometheus; on AWS only `/api` is exposed by the load balancer.
