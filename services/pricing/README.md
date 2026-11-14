# SwiftDrop Pricing & Zones

Java 21, Spring Boot 3.5.16, Maven, PostgreSQL 16, and Angular 20.

## Local development

Run these commands from the repository root in PowerShell. Docker Desktop must be running.

```powershell
docker compose up -d postgres
$env:POSTGRES_PORT = "5433"
mvn -f services/pricing/pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The local `.env` file must also contain `POSTGRES_PORT=5433` to publish Docker PostgreSQL on that port.
The default port for teammates without this override is 5432. The database is `pricing_db`, owned by
`pricing_svc`. Development defaults match Docker Compose. Flyway applies the schema migrations at startup.

Pricing listens on port 8085. Its health endpoint is `http://localhost:8085/actuator/health`.

In another terminal, from the repository root, start the Pricing UI.

```powershell
cd frontend
npm ci
npm run start:pricing
```

Open `http://localhost:4205`. The Angular development proxy forwards `/api` requests to port 8085.

Choose Administrator and connect a development account to manage zones and rate cards.
Sign out and choose Customer or Merchant to estimate a fee. Local tokens stay in memory and expire
after one hour. Reloading the page requires reconnecting. The development token endpoint exists only
when the backend runs with the `dev` profile. Production frontend builds hide that helper.
Production authentication needs the team's Identity integration. Existing bearer tokens can be supplied
through the access-token form.

## Current API

All admin paths require an Admin JWT.

| Method | Path | Action |
|---|---|---|
| GET / POST | `/api/v1/admin/zones` | List / create zones |
| GET / PUT / DELETE | `/api/v1/admin/zones/{id}` | Read / update / deactivate zone |
| GET / POST | `/api/v1/admin/rate-cards` | List / create rate cards |
| GET / PUT / DELETE | `/api/v1/admin/rate-cards/{id}` | Read / update / deactivate rate card |
| GET / POST | `/api/v1/admin/promo-codes` | List / create promo codes |
| GET / PUT / DELETE | `/api/v1/admin/promo-codes/{id}` | Read / update / deactivate promo code |
| GET | `/api/v1/pricing/zones` | Active zone directory for Admin, Customer, Merchant |
| POST | `/api/v1/pricing/estimate` | Fee estimate for Customer or Merchant |

List endpoints accept zero-based `page` and `size` between 1 and 100. The UI loads all pages.
Deletion deactivates records and retains their history. Routes are directional. A rate card is effective
from `validFrom` inclusive to `validTo` exclusive. An empty end date means indefinitely.
PostgreSQL's `btree_gist` extension enforces non-overlapping active rates for each route, including
concurrent inserts. Migration credentials must be allowed to install that trusted extension.

```json
{
  "originZone": "COLOMBO",
  "destinationZone": "KANDY",
  "weightKg": 2,
  "sizeClass": "MEDIUM",
  "deliveryType": "STANDARD"
}
```

Estimates use the Sri Lanka calendar date, LKR, and a weight greater than zero and at most 20 kg,
with up to three decimal places. Sizes are SMALL, MEDIUM, LARGE. The Standard strategy adds the
base fee, per-kilogram weight charge, and size surcharge. Money is rounded to two decimals using
HALF_UP. Express delivery multiplies the Standard subtotal by the rate card's optional `expressMultiplier`.
The additional amount is itemized as `expressAmount`. Existing cards require an administrator to configure
the multiplier before Express is available. Multipliers must be at least 1 and have at most two decimal places.
Use `"deliveryType": "EXPRESS"` to request that strategy.
Each quote records its authenticated owner and amount snapshot and expires after 15 minutes.
The Parcel service accepts the quote when a booking is confirmed (see below).

Add an optional `promoCode` field to the estimate request to apply a discount. Codes are case-insensitive.
The promo must be enabled, within its Sri Lanka date range, below its optional redemption limit, and
meet the minimum fare before discount. Percentage and fixed LKR discounts apply to the subtotal
including Express, round HALF_UP, and cannot make the total negative. Quote snapshots retain the
promo ID, code, and discount amount. Repeated estimates do not consume redemption uses; a use is
counted only when the quote is accepted at booking.

## Internal API: accepting a quote at booking

Agreed with Member 4. The Parcel service calls this once a booking is confirmed. It is protected by the shared
service secret in the `X-Internal-Token` header (`INTERNAL_API_TOKEN`; the dev profile defaults to
`dev-internal-token`, and with no token configured nothing is accepted). User JWTs are not accepted here.

```
POST /internal/quotes/{quoteId}/accept
X-Internal-Token: <shared secret>
{ "parcelId": "uuid" }            (body optional)
```

```json
{ "quoteId": "uuid", "parcelId": "uuid", "acceptedAt": "2026-11-11T20:00:00Z",
  "originZone": "COLOMBO", "destinationZone": "KANDY", "strategyUsed": "STANDARD",
  "promoCode": "SAVE10", "currency": "LKR",
  "pricing": { "baseAmount": 300.00, "weightAmount": 200.00, "sizeAmount": 50.00,
               "expressAmount": 0.00, "discountAmount": 55.00, "totalAmount": 495.00 } }
```

| Answer | Meaning |
|---|---|
| 200 | Accepted. A retry of the same quote returns the same snapshot and does not use the promo again. |
| 401 | Missing or wrong `X-Internal-Token` |
| 404 | Unknown quote |
| 409 | The promo code can no longer be used (used up, disabled or out of date), or the quote was already accepted for a different parcel: request a new estimate |
| 410 | The quote is older than 15 minutes: request a new estimate |

Store the `pricing` object with the parcel and send it unchanged in `parcel.status.delivered`: the Document
service makes the receipt from exactly these fields (`docs/events/document-service-events.md`).
The quote row is locked while it is accepted, so simultaneous or retried calls cannot use a promo twice, and the
promo use is a single conditional update, so the last use of a code cannot be taken by two bookings.

For a local demo, create `SAVE10` with type PERCENT, value 10, minimum fare 0, and a start date of
today or earlier. The Standard demo quote becomes LKR 495. With Express multiplier 1.25, it becomes
LKR 618.75. These codes are entered through the admin UI and are not automatically seeded.

Configure `IDENTITY_JWKS_URI` for real JWT verification, optionally with `JWT_ISSUER`.
The shared local HS256 secret is accepted only in the `dev` profile. Supply production datasource
credentials through `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD`.

## Verification

```powershell
mvn -f services/pricing/pom.xml test
cd frontend
npm run build:pricing
```

Backend tests require Docker and create disposable PostgreSQL containers. They do not use the
development database. Browser verification created demo Colombo and Kandy zones and a directional
rate with base 300, per kg 100, and medium surcharge 50. A 2 kg medium parcel produces LKR 550.

Merchant-contract strategies, promo redemption at booking, weight bands, district/postal mapping,
Parcel integration, receipts, and the AI assistant are subsequent milestones.
The Pricing app is an isolated Angular workspace app for integration into the team's customer and admin apps.
