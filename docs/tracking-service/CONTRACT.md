# Tracking Service and Public Tracking Page: contract (Member 8)

Backend: `services/tracking`. Frontend: the public tracking page and the shared real-time hook (Angular).
Anything marked **ASSUMED** is owned by another member and was guessed; it is isolated in one class
(`event/EventMapper`) so it can be changed in one place. Please tell M8 if your events differ.

## 1. Scope (work split)

Event consumer, Redis read model, WebSocket/SSE push (SSE chosen), public tracking by number (non-sensitive data only),
polling fallback, ETA estimate. Frontend: mobile-friendly public tracking page (timeline, live map) and a real-time
hook shared with the customer app. Tests: event-to-push integration, public endpoint leaks no personal data.

## 2. Stack and conventions

Same as the other services: Java 21, Spring Boot 3.5.x, Maven, package `lk.swiftdrop.tracking`, artifact
`lk.swiftdrop:tracking-service`, port 8080 in the container and **8088 on the host**, Actuator `health` + `prometheus`,
JaCoCo line coverage >= 70% on `mvn verify`, standard error body (`timestamp, status, error, code, message, path,
correlationId`), `X-Correlation-Id` read, echoed and put in the MDC. No database: the read model is Redis only.

## 3. Public API (no login)

| Method and path | Response |
|---|---|
| `GET /api/v1/tracking/{trackingNo}` | `200 PublicTrackingResponse`, `Cache-Control: no-store`. `400 INVALID_TRACKING_NUMBER`, `404 TRACKING_NOT_FOUND`, `503 SERVICE_UNAVAILABLE` (Redis down). |
| `GET /api/v1/tracking/{trackingNo}/stream` | `text/event-stream`. First event = current state, then one event per change. Errors as JSON (404 etc.), `503 STREAM_LIMIT_REACHED` when the instance is full (the client polls instead). |

Tracking numbers are case-insensitive, letters, digits and inner dashes, 6 to 32 characters (`SD-2641-7788`,
`SD24000123`, `SBX123456789`).

SSE format: `event: tracking`, `id: <version>`, `retry: 3000`, `data: <PublicTrackingResponse JSON>`; a comment line
(`:keep-alive`) every 20 s. Streams time out after 30 minutes; `EventSource` reconnects by itself.

`PublicTrackingResponse` (also the payload of every SSE event):

```json
{
  "trackingNo": "SD-2641-7788",
  "status": "OUT_FOR_DELIVERY", "statusLabel": "Out for delivery", "tone": "PROGRESS",
  "stop": 4, "totalStops": 5, "terminal": false,
  "origin":      {"city": "Colombo", "point": {"lat": 6.93, "lng": 79.86}},
  "destination": {"city": "Kandy",   "point": {"lat": 7.29, "lng": 80.63}},
  "parcel": {"size": "SMALL", "weightKg": 2.00, "serviceType": "EXPRESS"},
  "stops": [
    {"stop": 1, "status": "CREATED",          "label": "Created",          "at": "2026-11-13T02:42:00Z", "state": "DONE"},
    {"stop": 2, "status": "PICKED_UP",        "label": "Picked up",        "at": "2026-11-13T04:35:00Z", "state": "DONE"},
    {"stop": 3, "status": "IN_TRANSIT",       "label": "In transit",       "at": "2026-11-13T06:00:00Z", "state": "DONE"},
    {"stop": 4, "status": "OUT_FOR_DELIVERY", "label": "Out for delivery", "at": "2026-11-13T08:50:00Z", "state": "CURRENT"},
    {"stop": 5, "status": "DELIVERED",        "label": "Delivered",        "at": null,                   "state": "PENDING"}
  ],
  "history": [
    {"status": "OUT_FOR_DELIVERY", "label": "Out for delivery", "at": "2026-11-13T08:50:00Z", "location": "Kandy hub", "note": null}
  ],
  "eta": {"from": "2026-11-13T10:10:00Z", "to": "2026-11-13T10:40:00Z", "basis": "GPS"},
  "deliveredAt": null,
  "failure": null,
  "courier": {"position": {"lat": 7.281, "lng": 80.612}, "at": "2026-11-13T09:58:41Z",
              "trail": [{"lat": 7.25, "lng": 80.55}], "distanceKm": 2.4},
  "lastUpdatedAt": "2026-11-13T09:58:42Z",
  "version": 12
}
```

* `status`: the public status. `ASSIGNED` is internal and shown as `CREATED`.
* `tone`: `PROGRESS`, `ATTENTION` (rescheduled), `DONE` (delivered), `EXCEPTION` (failed, returned, cancelled).
* `stops[].state`: `DONE`, `CURRENT`, `ATTENTION`, `EXCEPTION`, `PENDING`. An exception replaces the stop where it happened
  (FAILED and RESCHEDULED at 4, RETURNED at 5, CANCELLED at 1).
* `eta.basis`: `GPS` (out for delivery with a position under 15 minutes old: distance x 1.3 at 25 km/h + 5 min) or
  `SCHEDULE` (typical time from the start of the current stage: created 24 h, picked up 18 h, in transit 8 h, out for
  delivery 3 h; express halves it; deliveries 08:00-20:00 Colombo time). No ETA for FAILED and finished parcels.
* `failure.code`: `NOT_HOME`, `ADDRESS_NOT_FOUND`, `REFUSED`, `DAMAGED`, `ACCESS_RESTRICTED`, `WEATHER`, `OTHER`.
* `courier`: only while `IN_TRANSIT` or `OUT_FOR_DELIVERY`, rounded to about 100 m, hidden when older than 15 minutes.

### Privacy (work-split test "public endpoint leaks no personal data")

The service never stores personal data, so it cannot leak it: `EventMapper` reads a fixed whitelist of fields (ids,
status, city names, city-level coordinates rounded to ~1 km, size, weight, service type, hub name, failure code,
courier coordinates rounded to ~100 m). Names, phone numbers, e-mail addresses, street addresses, postal codes, notes,
customer/merchant ids and free-text failure reasons are ignored. Hub and city names that look like contact details
(an `@`, or five or more digits) are dropped. The response has no field for any of them and no internal ids
(`PublicTrackingPrivacyTest` checks values, field names, the SSE stream and the stored record).

## 4. Events consumed

Queue `tracking.q` (durable, `x-dead-letter-exchange=swiftdrop.dlx`, `x-dead-letter-routing-key=tracking.q.dlq`),
DLQ `tracking.q.dlq` bound to `swiftdrop.dlx` with key `tracking.q.dlq`, both declared by this service on start-up.
Bindings on `swiftdrop.events`: **`parcel.#`** and **`courier.location.#`** (note `parcel.*` would not match
`parcel.status.delivered`). Competing consumers: run as many tracking instances as needed.

Envelope (M9): `eventId`, `eventType`, `occurredAt` required; `correlationId`, `producer`, `aggregateId`, `version`
optional; the body may be called `data` or `payload`.

| eventType | Fields read from `data` (**ASSUMED**, alternatives in brackets) |
|---|---|
| `parcel.created` (M4) | `trackingNo` [`trackingNumber`], `parcelId` [aggregateId], `sequence`, `pickupCity` [`pickup.city`, `origin.city`, `pickupZoneName`], `pickupLat`/`pickupLng` [`pickup.lat`/`.lng`], `dropoffCity`, `dropoffLat`/`dropoffLng` (same alternatives), `size` [`parcel.size`], `weightKg` [`parcel.weightKg`], `serviceType` [`deliveryType`] |
| `parcel.status.<status>` (M4) | `trackingNo` or `parcelId`, `sequence`, `hub` [`hubName`], `courierId`, `failureReason` [`reason`] for `failed`. `<status>` is `picked_up`, `in_transit`, `out_for_delivery`, `delivered`, `failed`, `rescheduled`, `returned`, `cancelled`, `created`, `assigned` |
| `parcel.assigned` / `parcel.reassigned` (M6) | `trackingNo` or `parcelId`, `courierId` [`newCourierId`]; `toStatus` optional on reassigned |
| `parcel.cancelled` | `trackingNo` or `parcelId` |
| `courier.location.updated` (M7) | `courierId` [aggregateId], `lat`/`lng` [`latitude`/`longitude`, `location.lat`/`.lng`], `recordedAt`; optional `trackingNo` or `parcelId` to target one parcel |

Rules:

* **Ordering.** M4 should put a per-parcel `sequence` (1, 2, 3, ...) in every parcel event. An event whose sequence is
  not newer than the last applied one does not change the status (it is still added to the history). Without a
  sequence, `occurredAt` decides. Assignment events from M6 are ordered by `occurredAt` only.
* **Duplicates.** Event ids are remembered (Redis `tracking:event:<id>`, 7 days), so redelivery is harmless.
* **Routing positions.** A courier position is applied to the parcels assigned to that courier (from
  `parcel.assigned`) that are in transit or out for delivery.
* **Failures.** Unreadable or id-less messages go straight to the DLQ; other failures are retried 3 times with backoff,
  then dead-lettered. An event that names only an unknown `parcelId` is retried (its `parcel.created` may be late).

## 5. Redis keys

`tracking:parcel:<trackingNo>` (JSON, TTL 30 days, refreshed on write, optimistic locking with WATCH/MULTI/EXEC),
`tracking:pid:<parcelId>`, `tracking:courier:<courierId>` (set), `tracking:event:<eventId>`; channel
`tracking:updates` carries every changed record to all instances, each pushes it to its own open streams.

## 6. What other members need to do

* **M1 (gateway)**: route `GET /api/v1/tracking/**` to `tracking:8080` without authentication (public); do not buffer
  `text/event-stream` responses and allow long-lived connections (at least 30 minutes, or let the client reconnect);
  rate-limit `/api/v1/tracking/**` per IP. Never route `/api/v1/dev/**`.
* **M4 (parcel)**: include `trackingNo`, `parcelId` and `sequence` in `parcel.created` and every `parcel.status.*`;
  city names (and coordinates if you have them) for pickup and dropoff in `parcel.created`. Use the failure codes above.
* **M6 (dispatch)**: include `trackingNo` (or `parcelId`) and `courierId` in `parcel.assigned` / `parcel.reassigned`.
* **M7 (courier)**: publish `courier.location.updated` with `courierId`, `lat`, `lng`, `recordedAt`.
* **M9 (broker)**: add `tracking.q` with the arguments above and the two bindings to `definitions.json`.
* **M4 / customer app**: track a parcel with the shared real-time hook (section 8) rather than polling, and link
  "Track" buttons to `/track/<trackingNo>`. The merchant service already builds `trackingUrl` from
  `PUBLIC_TRACKING_URL` (default `http://localhost:4200/track`).

## 7. Dev tools (profile `dev` only)

`POST /api/v1/dev/tracking/simulate` plays a whole journey through the real path; `POST /api/v1/dev/tracking/events`
publishes any envelope to `tracking.q`. Both publish through the default exchange straight to `tracking.q`, so no other
service receives simulated events.

## 8. Frontend (Angular, `frontend/projects/merchant-portal`)

| Path | What |
|---|---|
| `/track`, `/track/:trackingNo` | Public tracking page (no sign-in, full screen, Route Board design): hero board with the transit line and ETA, live map, history, details. Mobile first. |
| `core/tracking/tracking-stream.ts` | **Shared real-time hook** `injectTrackingStream()`, for this page and the customer app. |
| `core/tracking/tracking.api.ts` | `TrackingApi` (GET), `normalizeTrackingNo()`, `EVENT_SOURCE_FACTORY` (replaced by a fake in specs). |
| `core/models/tracking.model.ts` | TypeScript shape of `PublicTrackingResponse`. |
| `shared/ui/transit-line/` | `<app-transit-line [stops]="..." variant="board|light">`, the numbered-stops line, reusable on "My parcels". |
| `features/tracking/route-map/` | SVG route map: no map tiles or third-party scripts, so it works under the portal's content security policy. |

Using the hook in any component:

```ts
readonly tracking = injectTrackingStream();          // in a field initialiser or constructor
ngOnInit() { this.tracking.watch(this.trackingNo); }  // any spelling; "sd-2641-7788" works
// template: tracking.parcel()?.statusLabel, tracking.parcel()?.eta, tracking.state() === 'live'
```

It loads the current state, opens the SSE stream, applies only newer versions, falls back to polling every 15 s while
the stream is down (and reopens it after 30 s if the browser gave up), stops everything when the parcel is delivered,
returned or cancelled, and cleans up when the component is destroyed. `state()` is one of `idle`, `loading`,
`connecting`, `live`, `polling`, `finished`, `not-found`, `error`.

Routing of `/api/v1/tracking`: the dev server proxies it to `localhost:8088` (`proxy.conf.json`); the portal container's
nginx sends it to `TRACKING_UPSTREAM` (default `http://tracking:8080`) with buffering off for the event stream. Once the
API gateway (M1) exists, point both at the gateway instead.
