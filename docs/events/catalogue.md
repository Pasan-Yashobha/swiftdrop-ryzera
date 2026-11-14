# SwiftDrop event catalogue

Owner: Member 9 (event backbone). This file and [`envelope.schema.json`](envelope.schema.json) are the contract between
producers and consumers. Figure 3 of the integration design (section 18.20 of the group document) shows the same
catalogue as a diagram.

## Rules

- One topic exchange, `swiftdrop.events` (durable). The routing key is always the `eventType`.
- Every message uses the [envelope](envelope.schema.json): `eventId, eventType, version, occurredAt, producer,
  correlationId, aggregateId, data`. All fields are mandatory.
- `data` carries **ids only**. No email, phone number or address ever goes into an event; the Notification Service
  looks contact details up in Identity.
- Each event has exactly one producer. Only the Parcel Service publishes `parcel.status.*`.
- Fields can be added, never renamed or removed. A breaking change needs `version: 2`.
- Producers publish through a transactional outbox, persistent delivery mode, content type `application/json` and
  publisher confirms. Consumers are idempotent on `eventId` (a `processed_events` table) because delivery is
  at-least-once.
- Money is a decimal **string** (`"1250.00"`), currency `"LKR"`. Times are ISO-8601 UTC.

## Events

| eventType | Producer | aggregateId | data | Consumers (queue) |
|---|---|---|---|---|
| `parcel.created` | parcel-service | parcelId | `parcelId, trackingNo, merchantId?, customerId` | merchant (`merchant.orders.q`), notification, webhook |
| `parcel.assigned` | parcel-service | parcelId | `parcelId, trackingNo, courierId, merchantId?` | merchant, webhook, audit |
| `parcel.status.<status>` (`picked_up`, `in_transit`, `out_for_delivery`, `delivered`, `failed`, `rescheduled`, `returned`) | parcel-service | parcelId | `parcelId, trackingNo, fromStatus, toStatus, courierId, customerId, merchantId?, etaMinutes?, failureReason?` | merchant, notification, webhook, tracking, reporting; `delivered` also document (`document.receipts.q`) |
| `parcel.cancelled` | parcel-service | parcelId | `parcelId, trackingNo, merchantId?, customerId, reason?` | merchant, notification, webhook |
| `courier.availability.changed` | courier-service | courierId | `courierId, available, zoneId?` | dispatch (`dispatch.courier.q`) |
| `merchant.registered` | merchant-service | merchantId | `merchantId, businessName` | audit |
| `merchant.approved` | merchant-service | merchantId | `merchantId, decidedBy` | notification (email to owner), audit |
| `merchant.rejected` | merchant-service | merchantId | `merchantId, decidedBy, reason` | notification (email to owner), audit |
| `merchant.apikey.rotated` | merchant-service | merchantId | `merchantId, keyPrefix` | audit |
| `receipt.generated` | document-service | receiptId | `receiptId, parcelId, customerId, trackingNo, totalAmount, currency` | notification (email with the PDF attached) |
| `invoice.generated` | document-service | invoiceId | `invoiceId, merchantId, periodStart, periodEnd, totalAmount, currency` | webhook (merchant callback) |
| `webhook.delivery.exhausted` | webhook-service | deliveryId | `deliveryId, merchantId, eventId, eventType, attempts, lastHttpStatus` | notification (email to merchant), audit |
| `webhook.delivery.replayed` | webhook-service | deliveryId | `deliveryId, merchantId, eventId, replayedBy` | audit |

`merchantId?` means the field is present only for parcels booked by a merchant. The Webhook Delivery Service skips
events without it.

## Example

```json
{
  "eventId": "7c1e9a52-0f4b-4a52-9d3e-2b8f0f1c6a11",
  "eventType": "parcel.status.out_for_delivery",
  "version": 1,
  "occurredAt": "2026-11-01T08:30:12Z",
  "producer": "parcel-service",
  "correlationId": "b41d0c2e-6a7f-4c55-9f0e-1c2d3e4f5a6b",
  "aggregateId": "PCL-000123",
  "data": {
    "parcelId": "PCL-000123", "trackingNo": "SD24000123", "fromStatus": "IN_TRANSIT",
    "toStatus": "OUT_FOR_DELIVERY", "courierId": "CR-014", "customerId": "USR-2201",
    "merchantId": "MER-031", "etaMinutes": 45
  }
}
```

## Queues

Each consumer service has one durable work queue with `x-dead-letter-exchange=swiftdrop.dlx` and
`x-dead-letter-routing-key=<its dlq>`; the `.dlq` queue is bound to the direct exchange `swiftdrop.dlx` with its own
name. Services declare their own topology on startup, and [`infra/rabbitmq/definitions.json`](../../infra/rabbitmq/definitions.json)
declares the same thing (loaded by the `rabbitmq-init` container in docker-compose). The arguments must match exactly,
otherwise RabbitMQ refuses the declaration with `PRECONDITION_FAILED`. When you add a queue to your service, add it to
`definitions.json` in the same PR.

| Queue | Owner | Bound to |
|---|---|---|
| `notification.q` / `notification.dlq` | notification (M9) | `parcel.created`, `parcel.status.*`, `parcel.cancelled`, `receipt.generated`, `merchant.approved`, `merchant.rejected`, `webhook.delivery.exhausted` |
| `webhook.q` / `webhook.dlq` | webhook (M9) | `parcel.created`, `parcel.assigned`, `parcel.cancelled`, `parcel.status.*`, `invoice.generated` |
| `webhook.delay.5s`, `.30s`, `.2min`, `.10min`, `.30min` | webhook (M9) | exchange `swiftdrop.webhook.delay` (direct), TTL then dead-lettered to `webhook.retry.q` |
| `merchant.orders.q` / `merchant.orders.dlq` | merchant (M3) | `parcel.created`, `parcel.status.*`, `parcel.assigned`, `parcel.cancelled` |
| `dispatch.courier.q` / `dispatch.courier.q.dlq` | dispatch (M6) | `courier.availability.changed` |
| `audit.q` / `audit.dlq` | audit (M1) | `user.#`, `merchant.#`, `admin.#`, `parcel.assigned`, `webhook.delivery.#` |
| `document.receipts.q` / `document.receipts.dlq` | document (M5) | `parcel.status.delivered` |

Consumer DLQs are replayed by hand with "Move messages" in the RabbitMQ management UI (http://localhost:15672; the
shovel plugins are enabled in docker-compose). Webhook deliveries also have a Replay button in the admin console.
