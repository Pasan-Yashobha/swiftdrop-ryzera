# Document service: events

Owner: Member 5. Follows the event catalogue in the group document (18.20.3, Figure 43) and what Member 9
confirmed for the broker. Exchange `swiftdrop.events` (topic, durable); dead-letter exchange `swiftdrop.dlx`
(direct, durable). Messages are persistent JSON (`application/json`), the routing key is the event type.

## Envelope (all fields mandatory)

```json
{
  "eventId": "uuid v4",
  "eventType": "receipt.generated",
  "version": 1,
  "occurredAt": "2026-11-11T10:00:00Z",
  "producer": "document-service",
  "correlationId": "carried over from the incoming event, or a new UUID",
  "aggregateId": "id of the thing the event is about",
  "data": { }
}
```

Events carry identifiers and amounts only, never phone numbers or addresses. Amounts are decimal strings with two
decimals, for example `"1250.00"`. New fields may be added; existing ones are never renamed or removed.

## Published

### `receipt.generated` (consumed by Notification)

Published once per new receipt, by the transactional outbox. `aggregateId` is the parcel id.

```json
{ "parcelId": "uuid", "documentId": "receipt uuid", "receiptNo": "RCP-...",
  "downloadUrl": "/api/v1/receipts/{documentId}/download",
  "customerId": "Identity user id", "trackingNo": "SD-2641-7788",
  "totalAmount": "618.75", "currency": "LKR" }
```

The first three fields are the catalogue's (`parcelId, documentId, downloadUrl`); the rest were agreed with
Member 9 for the email. The PDF itself is not in the event. Notification attaches it by calling
`GET /internal/receipts/{documentId}/pdf` with the header `X-Internal-Token` (the shared `INTERNAL_API_TOKEN`).

### `invoice.generated` (becomes a merchant webhook event)

Published once per new invoice. `aggregateId` is the invoice id.

```json
{ "invoiceId": "uuid", "invoiceNo": "INV-202610-...", "merchantId": "uuid, only if known",
  "ownerUserId": "Identity user id of the merchant's owner",
  "periodStart": "2026-10-01", "periodEnd": "2026-10-31",
  "totalAmount": "1856.25", "currency": "LKR", "lineCount": 3,
  "downloadUrl": "/api/v1/invoices/{invoiceId}/download" }
```

`merchantId` is the merchant's id in the Merchant service, which is what the webhook service looks a merchant up
by. Admin supplies it when generating the invoice (`merchantServiceId`). Without it the field is left out and the
event cannot be addressed to a webhook.

## Consumed

### `parcel.status.delivered` (queue `document.receipts.q`)

Bound with the routing key `parcel.status.delivered`. The fields below were **agreed with Member 4 (Parcel)**; the
Parcel service is not built yet, and they are read in one class (`DeliveredParcelMapper`), so any later change stays
small.

```json
{ "parcelId": "uuid", "trackingNo": "SD-2641-7788", "customerId": "Identity user id (JWT subject)",
  "deliveredAt": "2026-11-11T10:00:00Z",
  "merchantId": "optional", "courierId": "optional",
  "pricing": { "baseAmount": 300.00, "weightAmount": 200.00, "sizeAmount": 50.00,
               "expressAmount": 137.50, "discountAmount": 68.75, "totalAmount": 618.75, "currency": "LKR" } }
```

- `parcelId` and `trackingNo` are required. `customerId` is the customer's Identity user id; a parcel without one
  (a merchant's parcel) gets no receipt, because merchants are invoiced instead.
- The delivery time is `data.deliveredAt` (UTC ISO-8601, the moment of delivery). The envelope's `occurredAt` is when
  the event was created and is only used when `deliveredAt` is missing. An unreadable `deliveredAt`, or one more
  than 5 minutes in the future, is a permanent failure (dead-letter queue).
- `pricing` is the snapshot the Parcel service received from Pricing when it accepted the quote at booking
  (`POST /internal/quotes/{quoteId}/accept`, see `services/pricing/README.md`); its `pricing` object has exactly
  these field names, so Parcel can copy it as it is. Receipts never recalculate prices.
- The Parcel service publishes the event through its transactional outbox once per transition to `DELIVERED`
  (a terminal state). Delivery is at-least-once, so this consumer deduplicates on `eventId`.
- Amounts may be numbers or decimal strings. `expressAmount` and `discountAmount` default to 0; `totalAmount`
  defaults to the sum and, when given, must equal base + weight + size + express - discount.
- If `customerId`, `trackingNo` or `pricing` are missing and `DOCUMENT_PARCEL_URL` is set, the service asks
  `GET {DOCUMENT_PARCEL_URL}/internal/parcels/{parcelId}` with `X-Internal-Token` and reads the same field names
  from the answer.
- A parcel with no customer (a merchant's parcel) gets no receipt: it is acknowledged and skipped.

## Handling rules

| Situation | What happens |
|---|---|
| Same `eventId` again | Acknowledged and skipped (`processed_event` table) |
| Same parcel, new `eventId`, same data | The existing receipt is kept; no second `receipt.generated` |
| Bad JSON, a missing or invalid envelope field, invalid delivery data, an unknown parcel, a different receipt already exists (409) | Rejected without requeue: straight to `document.receipts.dlq`, no retry |
| Database or Parcel service down (5xx, timeout) | Retried, 3 attempts in all with growing pauses, then `document.receipts.dlq` |
| Missing `correlationId` | A new one is generated |

The receipt, the "processed" mark and the `receipt.generated` outbox row are written in ONE transaction, so a
failure leaves nothing half done. A requeue with a header counter is not used: a requeued message comes back
unchanged, so such a counter could never go up.

## Queues

| Queue | Settings |
|---|---|
| `document.receipts.q` | durable; `x-dead-letter-exchange=swiftdrop.dlx`; `x-dead-letter-routing-key=document.receipts.dlq`; bound to `swiftdrop.events` with `parcel.status.delivered`; prefetch 10 |
| `document.receipts.dlq` | durable; bound to `swiftdrop.dlx` (direct) with the key `document.receipts.dlq` |

The service declares both on start-up; the broker definitions file should mirror them with the same settings.
A dead-lettered message is replayed by hand in the RabbitMQ management UI.
