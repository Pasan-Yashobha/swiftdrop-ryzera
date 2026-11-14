# Tracking Service (Member 8)

Event consumer, Redis read model, live push over server-sent events (SSE), public tracking by number with no
personal data, polling fallback and an ETA estimate. The full contract with the other services is in
[`docs/tracking-service/CONTRACT.md`](../../docs/tracking-service/CONTRACT.md).

```
swiftdrop.events ──parcel.#, courier.location.#──▶ tracking.q ──▶ TrackingEventConsumer
                                                                   │  EnvelopeParser → EventMapper (whitelist, no PII)
                                                                   │  TrackingProjector (ordering, duplicates)
                                                                   ▼
                                       Redis  tracking:parcel:<no>  (WATCH/MULTI/EXEC)
                                                                   │  PUBLISH tracking:updates
                                                                   ▼
                              every instance: RedisUpdateSubscriber → TrackingStreamService → browsers (SSE)
GET /api/v1/public/tracking/{no}          current state (also the polling fallback)
GET /api/v1/public/tracking/{no}/stream   text/event-stream: current state, then one "tracking" event per change
(also under /api/v1/tracking/..., the gateway's route for signed-in users)
```

## Run

Infrastructure from the repository root: `docker compose up -d redis rabbitmq`.

| How | Command | URL |
|---|---|---|
| IDE / Maven (dev profile) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (Windows: `mvnw.cmd ...`) | http://localhost:8087 |
| Docker | `docker compose up -d --build tracking` | http://localhost:8087 |

## Try it without the Parcel and Courier services (dev profile only)

```bash
# Plays a whole journey (created → … → delivered) through the real broker, consumer, Redis and push path.
curl -X POST localhost:8087/api/v1/dev/tracking/simulate -H "Content-Type: application/json" \
     -d '{"origin":"Colombo","destination":"Kandy","stepSeconds":6}'
# → {"trackingNo":"SD-4821-0937", ...}

curl -N localhost:8087/api/v1/public/tracking/SD-4821-0937/stream   # watch the live events
curl    localhost:8087/api/v1/public/tracking/SD-4821-0937          # polling view
```

`"outcome":"FAILED"` plays a failed attempt (not home) followed by a reschedule. `POST /api/v1/dev/tracking/events`
publishes any envelope you write by hand. Simulated events go straight to `tracking.q`, so other services never see them.

## Test

```bash
./mvnw verify      # unit + integration tests and the 70% JaCoCo gate; report in target/site/jacoco
```

Tests run without Docker. `EventToPushContainersTest` (real RabbitMQ and Redis through Testcontainers) runs only when
Docker is available, as in CI.

| Work-split test | Where |
|---|---|
| Event-to-push integration | `integration/EventToPushIntegrationTest` (HTTP + SSE client), `integration/EventToPushContainersTest` (broker + Redis) |
| Public endpoint leaks no personal data | `api/PublicTrackingPrivacyTest` |

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATA_REDIS_HOST/PORT/PASSWORD` | dev: `localhost` / `6379` / `dev_redis_pw` | read model and update channel |
| `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | dev: `localhost` / `5672` / `swiftdrop` / `dev_rabbit_pw` | event source |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | only when a browser calls the service directly |
| `SERVER_PORT` | dev: `8087` (the gateway's port for tracking) | compose sets 8080 inside the container |

Other settings (`swiftdrop.tracking.*` in `application.yml`): SSE timeout, heartbeat and connection limits, which
statuses share the courier's position, ETA speed and delivery hours, TTLs.
