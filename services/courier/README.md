# Member 7 Courier integration

This service and the Courier Route Board implement Member 7's backend/PWA work.
The Parcel reference is the unmodified `services/parcel/` source imported from
`origin/feature/member4-parcel`, commit `fb5e7d63bdb5758e566aa760acdfbe413078ceb8`.
Importing that source does not establish a running cross-service deployment.

## Verified HTTP contracts

| Endpoint | Behaviour |
| --- | --- |
| `GET /api/v1/courier/deliveries/today` | Authenticated courier's assignment projection, using the Asia/Colombo business day. Historical/reassigned records may be present; this is not an authoritative Parcel status read. |
| `PATCH /api/v1/courier/deliveries/{parcelId}/status` | JSON or multipart; successful Courier response is HTTP 200 with `DeliveryStatusResponse`. Identity comes from `CurrentUser`, never a frontend courier ID. |
| `GET /api/v1/courier/profile` | Current authenticated profile. |
| `PUT /api/v1/courier/profile` | Existing profile create/update API. |
| `PATCH /api/v1/courier/profile/availability` | `{ "available": true/false }`; updates the local Courier profile only. |
| `POST /api/v1/courier/location` | One explicit GPS report; see [GPS_REPORTING.md](GPS_REPORTING.md). |

Courier JSON status fields are `status`, `recipientName`, `photoObjectKey`, `otp`,
and `failureReason`. Multipart uses the same scalar names and `proofPhoto` for the
binary image. The existing `photo` alias remains supported, but both upload parts
cannot be supplied together. Do not set a multipart Content-Type boundary manually.
Recipient names are limited to 100 characters by M7's database (M4 accepts 120),
object keys to 255, OTP to 10, and failure reason input to 500. M7 failure values are
`NOT_HOME`, `ADDRESS_NOT_FOUND`, `REFUSED`, `DAMAGED`, `ACCESS_RESTRICTED`, `WEATHER`, `OTHER`.
Proof requires a recipient and a validated JPEG/PNG/WebP image up to 5 MiB, or a
validated existing parcel-specific proof key. Only the key is stored in the database
and forwarded to Parcel. See [PROOF_STORAGE.md](PROOF_STORAGE.md).

`ParcelClient` sends `PATCH /internal/parcels/{parcelId}/status` to M4 using JSON:
`status`, server-derived `courierId`, and applicable `recipientName`, `photoObjectKey`,
`otp`, `failureReason`. M4's optional `note` is not sent by M7. M4 accepts but does
not verify OTP on this path. `X-Internal-Token` and the validated/generated
`X-Correlation-Id` are forwarded. Java 21 `JdkClientHttpRequestFactory` supports
PATCH with explicit three-second connection and read timeouts.

M4's success is **204 with no body**. Courier then updates its local projection,
proof row and workload, and returns its own response using existing tracking metadata
and Courier timestamps. It does not invent metadata from the empty response.
Parcel rejection/timeout prevents those local database mutations. A previously
uploaded object can remain orphaned because object storage and database rollback
are not one transaction; cleanup is not implemented in this stage.

Optional tracking lookup uses `GET /internal/parcels/{parcelId}`. Actual M4
`InternalParcelDetailResponse` provides `trackingNo` and pricing/customer metadata;
it provides **neither assignment courier nor status**. M7 also retains support for
the older `trackingNumber` field. GET I/O failures and JDK timeout cancellation return
empty metadata in production. Explicit demo tracking fallback remains separate;
mutations never fall back to simulated success.

## Status and error handling

M4 validates ownership before state changes, including same-state requests. M7 first
checks its persisted current-assignment cursor/history, rejects unverifiable/ambiguous
ownership and previous couriers, and preserves history on reassignment. M4 remains
authoritative for transitions. The Route Board offers a supported subset:

| Current status | PWA actions |
| --- | --- |
| ASSIGNED | PICKED_UP, FAILED |
| PICKED_UP | IN_TRANSIT, OUT_FOR_DELIVERY, FAILED |
| IN_TRANSIT | OUT_FOR_DELIVERY, FAILED |
| OUT_FOR_DELIVERY | DELIVERED, FAILED |
| FAILED | OUT_FOR_DELIVERY |
| DELIVERED / REASSIGNED | None |

M4 additionally supports RESCHEDULED, RETURNED and CANCELLED transitions which are
not implemented as Courier actions. Do not infer that every M7-accepted status string
is a legal transition from every current state.

| M4 response | M7 behaviour |
| --- | --- |
| 400 / 401 / 403 / 404 | Existing validation, internal-authentication, forbidden and not-found mappings remain. An M4 401 is an internal-token problem, not proof that a Courier JWT expired. |
| 409 `COURIER_MISMATCH` | Preserve 409 and code; PWA directs the courier to Dispatch. |
| 409 `IDEMPOTENCY_KEY_IN_USE` | Preserve 409 and code; do not submit another operation blindly. |
| 409 `OPTIMISTIC_LOCK` | Preserve 409 and code; state must be refreshed/checked. |
| Other/malformed 409 | Preserve 409 as `INVALID_STATE`, with a safe generic message. |
| 422 `IDEMPOTENCY_KEY_REUSED` | Preserve 422 and code; PWA blocks another submission pending investigation/refresh. |
| Other/malformed 422 | Preserve 422 as `PARCEL_REJECTED`; never misclassify it as an outage. |
| Connection/read timeout, cancellation, other communication failure | 503; no local successful status mutation and no automatic mutation retry. |

Conflict mappings consume only the upstream machine code, not upstream messages or
details. The existing correlation filter returns the request correlation ID to the
PWA. The PWA prevents overlapping submissions and changes state only after confirmed
backend success. Shared authentication can replay a 401 once after token refresh;
the verified M4 token filter rejects 401 before applying a Parcel mutation. Network
failures, 409, 422 and 5xx are not automatically retried.

## Idempotency decision

**M7 does not send an Idempotency-Key and does not claim end-to-end operation
idempotency.** No new random key or deterministic status-only key has been added.

M4's `CourierStatusController.updateStatus`, `ParcelService.updateCourierStatus`
and `IdempotencyService.startOrCheckReplay` establish these existing behaviours:

* The header is optional. With no key, a repeated same-state update is a no-op after
  assignment validation; this does not validate that repeated proof/failure payloads match.
* A new key hashes the parcel ID and full deserialized request using SHA-256 and
  commits an IN_PROGRESS record in a separate REQUIRES_NEW transaction.
* A completed identical request replays as 204 without another mutation.
* An IN_PROGRESS key returns 409 (checked before comparing payloads).
* A completed key with different data returns 422.
* Completion is recorded in the business transaction. Rejection or rollback after
  reservation can leave IN_PROGRESS behind. Seven-day expiry metadata is written,
  but the inspected source neither checks expiry nor provides an expiry/recovery job.

There is no durable M7 logical-operation identity or persisted outbound request to
reuse across an uncertain retry, restart, or browser reload. Multipart re-upload can
also generate a different photo key, changing the M4 request hash. Automatically
generating a key per attempt would be unsafe. Before adding keys, agree M4 reservation
recovery, persist M7 operation identity and the exact normalized request/proof key,
and reuse both for every retry of that operation. Different logical operations need
different IDs, including later delivery attempts. Do not work around an uncertain or
stuck request by rotating its key or changing its payload.

An M4 commit followed by a lost HTTP response or failed M7 commit can leave Parcel
ahead of Courier. Refreshing today's M7 projection alone cannot prove M4's outcome;
the current M4 internal GET cannot reconcile status/ownership. Check authoritative
Parcel state with its owner before an uncertain manual retry. Same-state no-op and
local terminal duplicate guards do not establish general exactly-once processing.

## Assignment and lifecycle limitations

Dispatch owns `parcel.assigned`/`parcel.reassigned`. M7 consumes the version-1 M6
envelope, including the existing one-layer JSON-string compatibility format. Event-ID
deduplication, logical duplicate checks, strictly older timestamp handling, history,
bounded transient retries and the existing Courier DLQ remain intact. A failed optional
tracking lookup does not fabricate tracking data or reject an otherwise valid assignment.

The inspected Dispatch assignment/reassignment paths update Dispatch and its outbox;
they do not update Parcel assignment. M7 must not manufacture ASSIGNED PATCH calls on
event receipt or claim that its projection proves M4 ownership. M4's ordinary ASSIGNED
path also rejects a different existing courier, so it is not an agreed reassignment API.
M4/M6 must define who synchronizes assignments and how reassignment reaches Parcel.

M4 allows FAILED -> OUT_FOR_DELIVERY. After M4 acknowledges that existing transition,
M7 now restores its local active-assignment count once and clears the current closure
time. The assignment ID/history and latest failure context are preserved; M4 retains
the authoritative attempt/status history. Duplicate updates do not repeatedly increment
the count, and rejection/timeout leaves the failed projection unchanged. This does not
reconcile missed events or define Dispatch count ownership. Availability events remain disconnected: M6 defaults missing
`activeDeliveries` to zero and can overwrite a newer Dispatch count with a delayed M7
snapshot. Its guard compares event time only with last assignment time; it does not
establish general availability ordering or event-ID deduplication. Agree that contract
before publishing `courier.availability.changed`; the M9 catalogue requires an outbox.

## Deployment configuration

Use environment/secret-store values, never checked-in production credentials.

| Setting | Required alignment |
| --- | --- |
| Courier `SERVER_PORT` | Default 8080; host-run Gateway defaults to Courier at `http://localhost:8086`, so use port 8086 or set Gateway `COURIER_URI` correctly. |
| Courier `PARCEL_SERVICE_URL` | Default `http://parcel:8080` for a service network. Host-run Parcel can use `PORT=8082`, with Courier pointing to `http://localhost:8082`. |
| Courier `INTERNAL_API_TOKEN` | Must equal Parcel `SWIFTDROP_SECURITY_INTERNAL_TOKEN`. Their development defaults differ. |
| Courier mock fallback | Set `SWIFTDROP_INTEGRATION_ALLOW_MOCK_FALLBACK=false` (standard Spring environment override) or `--swiftdrop.integration.allow-mock-fallback=false` for integration/production. The root `ALLOW_MOCK_FALLBACK` placeholder defaults false, but the existing `dev` profile hardcodes true; `ALLOW_MOCK_FALLBACK=false` alone must not be assumed to override that profile. |
| Courier `IDENTITY_JWKS_URI` / `JWT_ISSUER` | Non-dev requires a nonblank JWKS URI, RS256, valid token time and matching issuer (default `swiftdrop-identity`). HMAC fallback is confined to explicit dev/test profiles. |
| Database / RabbitMQ | Existing `SPRING_DATASOURCE_*` / `SPRING_RABBITMQ_*` settings; provision the Courier DB and run migrations V1-V3, plus the separate Parcel DB/migration. |
| Private proof storage | Set the documented S3/MinIO properties and provision a private bucket; see PROOF_STORAGE.md. |

The existing Angular Courier proxy targets Gateway on localhost:8080. Shared proxy,
Gateway, Compose, Identity and imported Parcel files have not been changed by this
hardening stage. A matching token alone does not configure databases, assignments,
RabbitMQ, proof storage or the Gateway upstream.

## Verification and live checklist

From `services/courier`, run `mvn verify` (Java 21, full tests and 70% JaCoCo gate).
Focused backend checks use
`mvn -Dtest=ParcelClientTest,ParcelClientTransportTest,DeliveryServiceTest,DeliveryControllerTest,AssignmentEventProcessingTest test`.
From `frontend`, run `npm test -- --project=merchant-portal --watch=false --browsers=ChromeHeadless`,
then `npm run build` for both existing portals. Tests use nonproduction fixtures;
local HTTP transport tests exercise PATCH/204, GET timeout and actual M4 error envelopes.
Database-backed assignment tests use H2, and broker boundaries remain mocked.

A live environment is **not verified by those tests**. Before claiming it works:

1. Provision the aligned services, secrets, databases/migrations, private storage,
   Gateway upstreams and RabbitMQ definitions; use a noncustomer Courier test account.
2. Establish matching assignment in M4/M6 through an agreed owner-controlled process.
3. Verify authenticated today/profile APIs via Gateway, reject another courier, and
   exercise PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED with a real image.
4. Observe M4 204 and real Parcel state/history/outbox, then verify M7 proof key,
   current assignment and workload. Verify Notification/Tracking consumers separately.
5. Reject an invalid transition and mismatched assignment; stop Parcel and verify
   failure without local successful mutation. Investigate uncertain outcomes rather
   than automatically retrying. Repeat the private-storage and explicit GPS checks
   documented in their respective guides.

## Local verification snapshot — 2026-11-18

| Check | Result |
| --- | --- |
| Focused backend client/service/controller tests | 86 passed |
| Database-backed assignment regression suite | 16 passed |
| Focused Courier PWA suite | 65 passed |
| Final full Courier `mvn verify` | 239 passed; zero failures, errors or skipped tests |
| JaCoCo line coverage | 93.78% overall; ParcelClient 88.55%; DeliveryService 95%; DeliveryController 100%; 70% gate passed |
| Full Merchant Portal frontend suite | 718 passed |
| Merchant Portal and Admin Console production builds | Both passed via `npm run build` |

An intermediate focused run hit the existing HTTP test's 15-second test ceiling
under low available RAM; production timeouts and test timing assertions were not
relaxed. The final serial backend run passed all 239 tests. An outdated success-fixture
assertion was corrected to match PICKED_UP -> IN_TRANSIT before that final run.
No live M7 -> M4 deployment or live MinIO/RabbitMQ delivery is established by these results.

## Remaining-work finalization

See [DEPLOYMENT.md](DEPLOYMENT.md) for the M7 container, proposed M1 Compose block,
non-dev JWT configuration and repeatable live procedure. [FINALIZATION_REVIEW.md](FINALIZATION_REVIEW.md)
records the current-stage changes, source/diagram/PWA evidence, contribution exclusions
and fresh verification. The earlier 2026-11-18 results above remain historical.
