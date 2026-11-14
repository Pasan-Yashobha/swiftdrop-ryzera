# Notification Service (Member 9)

Turns domain events into messages for people: email through SMTP (Mailpit in development) and SMS through an HTTP
provider (a mock built into this service in development). Spring Boot 3.5, Java 21, PostgreSQL (`notification_db`),
RabbitMQ. Container port 8080, host port **8088**.

## How it works

```
swiftdrop.events --(parcel.status.*, receipt.generated, merchant.approved, ...)--> notification.q
  -> parse + validate envelope            (bad JSON / missing field -> rejected -> notification.dlq)
  -> processed_events insert (eventId)    (seen before -> nothing new is sent: idempotent consumer)
  -> one PENDING notification per active template of that event type (EMAIL and/or SMS)
  -> for each channel: find recipient (Identity GET /internal/users/{id}, cached 10 min;
     merchant templates first ask Merchant GET /internal/merchants/{id} for the owner),
     render the template, send
       ok                  -> SENT
       temporary failure   -> RETRYING, tried again 30 s later by the retry job (only that channel)
       3rd failure         -> FAILED + the original event is copied to notification.dlq
       permanent (404 ...) -> FAILED at once
       no phone / email    -> SKIPPED
```

- Events carry ids only; contact details are fetched per attempt and never stored. The log keeps a masked
  recipient (`k***@gmail.com`, `07X XXX 4567`).
- Templates live in the `notification_templates` table (seeded by `V1__init.sql`) and can be edited from the admin
  console without a redeploy. Placeholders: `{{trackingNo}}`, `{{recipientName}}`, `{{trackingLink}}`,
  `{{businessName}}` and any field of the event's `data`.
- `receipt.generated` emails attach the PDF from Document Service `GET /internal/receipts/{id}/pdf`. A failed fetch
  counts as a failed email attempt.
- `webhook.delivery.exhausted` (from the Webhook Delivery Service) emails the merchant owner that their endpoint is down.
- Email and SMS sit behind the `EmailSender` and `SmsSender` interfaces; a real provider is configuration only
  (`SPRING_MAIL_*`, `SMS_API_URL`, `SMS_API_KEY`).
- Listener: acknowledged after the handler returns (Spring AMQP AUTO mode), prefetch 10, stateless retry 3 attempts
  with backoff (1 s, 2 s, max 10 s) for infrastructure failures, then `RejectAndDontRequeueRecoverer` -> DLQ.

## REST API (`ROLE_ADMIN`, via the gateway route `/api/v1/admin/notifications/**`)

| Method and path | Purpose |
|---|---|
| `GET /api/v1/admin/notifications?status=&channel=&eventType=&from=&to=&page=&size=` | Notification log (newest first) |
| `POST /api/v1/admin/notifications/{id}/resend` | Resend a FAILED notification once (409 `INVALID_STATE` otherwise) |
| `GET /api/v1/admin/notifications/sms-inbox?page=&size=` | What the mock SMS provider received |
| `GET /api/v1/admin/notifications/templates` | All templates |
| `PUT /api/v1/admin/notifications/templates/{id}` | `{"subject","body","active"}` |
| `POST /mock-sms/messages` | The mock SMS provider (`X-Api-Key`), only when `SMS_MOCK_PROVIDER_ENABLED=true` |

## Configuration

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | `notification_db` / `notification_svc` |
| `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | broker |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | SMTP (Mailpit `mailpit:1025`, inbox on http://localhost:8025) |
| `IDENTITY_JWKS_URI`, `JWT_ISSUER` | verifying admin JWTs (RS256) |
| `DEV_JWT_HMAC_SECRET` | dev/test profile only: HS256 tokens from the merchant dev endpoint |
| `INTERNAL_API_TOKEN` | `X-Internal-Token` for Identity, Merchant and Document calls |
| `IDENTITY_SERVICE_URL`, `MERCHANT_SERVICE_URL`, `DOCUMENT_SERVICE_URL` | other services |
| `SMS_API_URL`, `SMS_API_KEY`, `SMS_MOCK_PROVIDER_ENABLED` | SMS provider |
| `PUBLIC_TRACKING_URL` | base of `{{trackingLink}}` |
| `NOTIFICATION_RETRY_DELAY_MS` | delay between channel retries (default 30000) |

## Build and test

```bash
mvn -B verify      # unit + integration tests on H2, JaCoCo gate 70 % line coverage
```

The RabbitMQ consumer test (`NotificationBrokerIntegrationTest`) uses Testcontainers and is skipped when Docker is
not available. Run locally against the compose stack with `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run` (port 8088).

## Demo

1. Publish an event in the RabbitMQ UI (http://localhost:15672, exchange `swiftdrop.events`, routing key
   `parcel.status.out_for_delivery`) with a `customerId` of a real Identity user.
2. The email shows up in Mailpit (http://localhost:8025) and the SMS in the admin console, Notification logs ->
   Mock SMS inbox.
3. Publish the same message again: nothing new is sent (duplicate `eventId`).
