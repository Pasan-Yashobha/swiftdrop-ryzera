# M7 deployment and repeatable live verification

Source/configuration inspection: 2026-11-21. This is a runbook, not a live-test result.
The shared Compose file has PostgreSQL, RabbitMQ, Gateway and Identity, but no Courier,
Parcel or MinIO service block. Do not change shared Compose as part of the M7 contribution.
The existing PostgreSQL initializer already provisions courier_db owned by courier_svc;
it runs only on first volume initialization, so an existing volume must be checked by M1.

## M7-owned container

The new Dockerfile builds the existing Java 21 module and runs its fat jar as a non-root
user. Local proof storage has a writable /app/data/proofs directory. .dockerignore keeps
build output, backups under target, and local environment/log files out of the build context.
Neither image build nor container startup has been verified in the current unhealthy environment.

## Proposed shared Compose addition for M1

Insert the following under the existing services mapping only after M1 review. It uses
existing postgres/rabbitmq health checks and the existing swiftdrop network. Parcel is a
separate missing dependency; this block does not pretend to deploy it or MinIO.

```yaml
  courier:
    build:
      context: ./services/courier
    restart: unless-stopped
    stop_grace_period: 40s
    mem_limit: 768m
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    environment:
      SERVER_PORT: 8080
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/courier_db
      SPRING_DATASOURCE_USERNAME: courier_svc
      SPRING_DATASOURCE_PASSWORD: ${SERVICE_DB_PASSWORD:?set service DB password}
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672
      SPRING_RABBITMQ_USERNAME: ${RABBITMQ_USER:?set broker username}
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:?set broker password}
      IDENTITY_JWKS_URI: http://identity:8080/.well-known/jwks.json
      JWT_ISSUER: swiftdrop-identity
      INTERNAL_API_TOKEN: ${INTERNAL_API_TOKEN:?set shared internal API token}
      PARCEL_SERVICE_URL: ${COURIER_PARCEL_SERVICE_URL:-http://parcel:8080}
      SWIFTDROP_INTEGRATION_ALLOW_MOCK_FALLBACK: "false"
      STORAGE_TYPE: ${COURIER_STORAGE_TYPE:-local}
      STORAGE_LOCAL_DIR: /app/data/proofs
      S3_ENDPOINT: ${COURIER_S3_ENDPOINT:-}
      S3_BUCKET: ${COURIER_S3_BUCKET:-proof-photos}
      S3_REGION: ${COURIER_S3_REGION:-us-east-1}
      S3_ACCESS_KEY: ${COURIER_S3_ACCESS_KEY:-}
      S3_SECRET_KEY: ${COURIER_S3_SECRET_KEY:-}
      CORS_ALLOWED_ORIGINS: ${COURIER_CORS_ALLOWED_ORIGINS:-http://localhost:4200,http://localhost:4201}
    volumes:
      - courierproofs:/app/data/proofs
    ports:
      - "8086:8080"
    networks: [swiftdrop]
```

Also declare `courierproofs: {}` under the existing top-level volumes mapping for local
storage. A named volume must be writable by the image's app user; bind mounts need explicit
host ownership. A remote MinIO endpoint must use HTTPS: the current S3 adapter allows HTTP
only for loopback development, so http://minio:9000 is not a usable remote production endpoint.
The proposed endpoint is deliberately empty until a storage endpoint is explicitly chosen. For MinIO mode set STORAGE_TYPE=minio, a trusted HTTPS endpoint,
private bucket, region and dedicated access/secret credentials through the secret store.
Do not expose public object URLs or add public bucket policies. The adapter validates privacy
and fails instead of silently falling back. See PROOF_STORAGE.md for exact settings/tests.

Do not activate the dev/test profile with real Identity: those profiles intentionally use
HS256 test/development tokens. Non-dev now requires JWKS and validates RS256, token time
and the configured issuer (default swiftdrop-identity), matching M2/M1. Roles COURIER/ADMIN
are read from role or roles claims; identity is always derived server-side from the subject.
Set M4 SWIFTDROP_SECURITY_INTERNAL_TOKEN to the same secret as M7 INTERNAL_API_TOKEN;
their defaults differ. No production credential belongs in a committed file.

For a host-run Courier, set SERVER_PORT=8086, PARCEL_SERVICE_URL=http://localhost:8082,
Identity JWKS to the actual host endpoint, the real Courier DB credentials/port and broker
settings. The Gateway host default COURIER_URI is http://localhost:8086; Compose already
sets COURIER_URI=http://courier:8080. Root server default is 8080 and would otherwise
collide with a host Gateway. In all cases explicitly disable mock fallback with the direct
Spring override SWIFTDROP_INTEGRATION_ALLOW_MOCK_FALLBACK=false.

## Routing and infrastructure checks

* Angular development uses frontend/proxy.conf.json: the Courier regex precedes generic
  /api and targets Gateway localhost:8080; generic /api remains Merchant localhost:8083.
* The production Merchant nginx template proxies generic /api through API_UPSTREAM. Shared
  Compose defaults MERCHANT_PORTAL_API_UPSTREAM to Merchant, so set that upstream to
  http://gateway:8080 for Courier testing after checking other Merchant routes with M1/M3.
  This is deployment configuration, not a change to the shared nginx source.
* Existing durable swiftdrop.events topic exchange routes parcel.assigned and
  parcel.reassigned to courier.assignments.q. Its rejection DLQ is courier.assignments.dlq
  on swiftdrop.dlx. M7 attempts transient infrastructure failures three times, then rejects
  without infinite requeue. This is not exactly-once delivery.
* GPS uses courier.location.updated, version 1, courier-service producer, UUID eventId,
  UTC occurredAt, courier aggregateId, correlationId and data {courierId,lat,lng,recordedAt}.
  It is a JSON object, persistent, mandatory and publisher-confirmed. tracking.q already
  binds courier.location.#. See GPS_REPORTING.md for throttling and uncertain-confirm limits.
* Courier migrations V1-V3 create profiles, assignments, proof key records, processed events,
  an unused outbox table, assignment cursors and a location-report timestamp. The outbox
  table's existence does not establish an availability publisher; none is enabled.
* Camera capture/geolocation need a secure context (HTTPS or localhost) and user permission.
  File selection is the camera fallback. GPS is one user-triggered position, not continuous tracking.

## Repeatable live procedure (only on a healthy, already provisioned environment)

1. Check database existence/ownership without dropping or recreating anything:
   `docker compose exec postgres pg_isready` and check courier_db/courier_svc with M1.
   Check `docker compose ps`, actual Gateway/Courier health and broker/private storage.
2. Obtain a real short-lived COURIER token using M2's existing login flow and a noncustomer
   test account. Do not forge tokens or save them in screenshots/logs. Put it in the local
   PowerShell variable `$m7Token`; use a test parcel UUID in `$m7ParcelId`.
3. Read through Gateway, preserving request evidence without dumping credentials:
   `Invoke-RestMethod http://localhost:8080/api/v1/courier/profile -Headers @{Authorization="Bearer $m7Token"}`
   and the corresponding `/api/v1/courier/deliveries/today` endpoint. Verify another courier
   cannot update this parcel. M4 and M6 must already agree on its current assignment.
4. From an actually ASSIGNED parcel, submit the real supported next transition:
   `Invoke-WebRequest -Method Patch -Uri "http://localhost:8080/api/v1/courier/deliveries/$m7ParcelId/status" -Headers @{Authorization="Bearer $m7Token";"X-Correlation-Id"="m7-live-status-check"} -ContentType application/json -Body '{"status":"PICKED_UP"}'`
   Observe M7 200 and, separately at the Courier-to-Parcel boundary, actual M4 204; verify
   Parcel status/history/outbox and the Courier projection. M7 200 alone is not proof
   that the 204 was observed. Repeat only legitimate state changes for that test parcel.
5. Through the PWA attach a small valid JPEG/PNG/WebP, recipient name and optional OTP
   while OUT_FOR_DELIVERY. Verify only a parcel-specific object key crosses to M4 and
   remains private. Never put image bytes in a database or fabricate a public URL.
6. Submit a known-invalid transition or mismatch using safe test fixtures; verify rejection
   and unchanged local delivery state. Observe timeout behavior with an owner-approved
   fault fixture, not by stopping another member's real service without authorization.
7. Press Report my location with a test device's explicit consent. Observe actual 204,
   publisher ACK/no mandatory return, routing to M8 and its projection for an eligible
   test parcel. Confirmation alone is not proof the Tracking consumer finished.
8. For retry, M4 must confirm FAILED -> OUT_FOR_DELIVERY. M7 restores its local active count
   once and clears the current closure time; no automatic M4 assignment synchronization
   or Dispatch workload publication is invented. Investigate uncertain mutations before
   retrying: refreshing the local M7 list alone cannot establish authoritative M4 outcome.

## Current live evidence

On 2026-11-21, no relevant backend/frontend/storage ports were listening, Docker's service
was stopped and its Linux-engine pipe absent. The host also had low free physical/virtual
memory. Docker and real backend services were not started. No live Gateway->Courier->Parcel,
real database, MinIO or broker/Tracking success is claimed. Unit/transport fixtures and
rendered documentation are separate evidence; see FINALIZATION_REVIEW.md for fresh results.

## Additional shared nginx corrections required from M1/frontend owner

The actual frontend/nginx/merchant-portal.conf.template currently has:

* `client_max_body_size 2m`, below M7's 5 MiB image / 6 MiB multipart envelope limit.
  Set this limit to at least `6m` for Courier requests (preserving other route limits as desired).
* `Permissions-Policy` sets camera=() and geolocation=(). Allow `camera=(self)` and
  `geolocation=(self)` for the Courier surface, retaining microphone/payment restrictions
  and explicit browser permission. File-input camera capture remains device/browser dependent.
* `img-src` lacks `blob:`. Add it to the existing image-source list so M7's local
  URL.createObjectURL preview can display; this does not publish a storage object URL.

Do not relax script-src or use public storage URLs to work around these policies.
The current sources were inspected, not modified. Unit tests/dev proxy do not establish
that the nginx-served production surface permits camera, GPS, large images or previews.
