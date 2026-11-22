# SwiftDrop Document Service

Java 21, Spring Boot 3.5.16, PostgreSQL, Flyway, and OpenPDF 2.2.3.

## First receipt milestone

The service generates an A5 delivery receipt from a confirmed delivered-parcel snapshot, including
the itemized charges and discount. It preserves those amounts instead of recalculating current rates.
One immutable receipt is stored per parcel. Retrying the same snapshot returns the same receipt ID.
A different snapshot for an existing parcel returns 409. Concurrent duplicate requests are handled
by PostgreSQL's unique parcel constraint and one transaction for metadata and PDF bytes.

This milestone stores PDFs in PostgreSQL BYTEA. Private MinIO/S3 storage, receipt generation from
Receipts are also made from `parcel.status.delivered` events and announced with `receipt.generated`
(see Events below); merchant invoices are described after the API table. The checkout currently has no Parcel implementation or confirmed
receipt event payload. Production creation must use trusted delivery data from that integration.

## Run locally

Start Docker Desktop. From the repository root, in PowerShell, run the following commands.

```powershell
docker compose up -d postgres
$env:POSTGRES_PORT = "5433"
mvn -f services/document/pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The root local `.env` file should contain `POSTGRES_PORT=5433` so Docker publishes PostgreSQL on
that port. Database `document_db` and role `document_svc` are created by the team's initialization
script for fresh Docker volumes. Flyway creates the receipt table on startup.

The service runs on port 8092. Health is available at `http://localhost:8092/actuator/health`.

For deployment supply `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD`, along with `IDENTITY_JWKS_URI` and optionally `JWT_ISSUER`.
Local dev JWT verification uses the same development secret as Pricing. Override it with
`DOCUMENT_DEV_JWT_HMAC_SECRET` in this service and the matching value in Pricing. Production
startup requires JWKS verification. The dev generation adapter exists only with profile `dev`.

## API

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/dev/receipts` | Admin, development profile only |
| GET | `/api/v1/receipts?page=0&size=20` | Customer's own receipts, or all receipts for Admin |
| GET | `/api/v1/receipts/{parcelId}` | Owner or Admin |
| GET | `/api/v1/receipts/{receiptId}/download` | Owner or Admin |

The list page size is between 1 and 100. Customer identity always comes from the signed JWT
subject. Requests for another customer's metadata or PDF return 404. Downloads have
`application/pdf`, an attachment filename, and `Cache-Control: no-store`.

## Merchant invoices

An A4 invoice lists the parcels delivered for one merchant in one billing period, one line per
parcel, with a server-computed total. Admin creates invoices; a merchant reads only its own.

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/invoices/generate` | Admin |
| GET | `/api/v1/invoices?page=0&size=20` | Merchant's own invoices, or all for Admin |
| GET | `/api/v1/invoices/{id}` | Invoice with its lines, owner or Admin |
| GET | `/api/v1/invoices/{id}/download` | PDF, owner or Admin |

Example request body (amounts are LKR with at most two decimals; there is no total field):

```json
{
  "merchantId": "<merchant owner's user id (JWT subject)>",
  "merchantName": "Ceylon Spice Traders",
  "periodStart": "2026-10-01",
  "periodEnd": "2026-10-31",
  "lines": [
    { "parcelId": "<uuid>", "trackingNumber": "SD-1001-0001",
      "description": "Colombo to Kandy, Standard", "amount": 550.00 }
  ]
}
```

Rules:

- The total is the sum of the lines, computed by the server. A request cannot contradict its own lines.
- One invoice per merchant and period. Sending the same content again (any line order or decimal
  scale) returns the existing invoice; different content for that merchant and period returns 409.
- A parcel can be billed on one invoice only. A request containing an already-billed parcel returns 409
  and stores nothing.
- Up to 500 lines per invoice, each amount up to 9999999999.99. Long invoices continue on further
  pages with the table header repeated and the total on the last page.
- Ownership compares `merchantId` with the JWT subject. The Merchant service keeps its own merchant UUID
  but tokens carry the owner's user id, so that is the value used here. Another merchant's invoice returns
  404. Customers, couriers and dispatchers get 403.
- The billing lines are supplied by Admin for now. Pulling them from Parcel and Merchant delivery data is
  a later integration step, like the receipt event.

The development adapter accepts the following snapshot. `customerId` must match the customer's
JWT subject. Currency is LKR. The delivery timestamp must be in the past or present, all amounts
must be nonnegative with at most two decimal places, and the total must equal base plus weight
plus size plus Express minus discount.

```json
{
  "parcelId": "fb53cbd4-bc03-48d5-b43d-df391d033bd8",
  "customerId": "pricing-demo-customer",
  "trackingNumber": "SD-2641-7788",
  "deliveredAt": "2026-11-11T10:00:00Z",
  "baseAmount": 300.00,
  "weightAmount": 200.00,
  "sizeAmount": 50.00,
  "expressAmount": 137.50,
  "discountAmount": 68.75,
  "totalAmount": 618.75
}
```

Obtain a local Admin JWT from the running Pricing service to test the adapter.

```powershell
$admin = Invoke-RestMethod -Method Post -Uri http://localhost:8085/api/v1/dev/tokens `
  -ContentType application/json -Body '{"sub":"document-demo-admin","role":"ADMIN"}'
$headers = @{ Authorization = "Bearer " + $admin.accessToken }
# Save the example snapshot above as receipt.json in your local working directory.
$receipt = Invoke-RestMethod -Method Post -Uri http://localhost:8092/api/v1/dev/receipts `
  -Headers $headers -ContentType application/json -Body (Get-Content receipt.json -Raw)
Invoke-WebRequest -Uri ("http://localhost:8092/api/v1/receipts/" + $receipt.id + "/download") `
  -Headers $headers -OutFile "$env:TEMP/swiftdrop-receipt.pdf"
```

## Events (RabbitMQ)

The full contract, with JSON examples, is in [docs/events/document-service-events.md](../../docs/events/document-service-events.md).

- **Consumes** `parcel.status.delivered` from queue `document.receipts.q` and makes the receipt. Duplicates are
  skipped by `eventId`. A message that can never be handled (bad JSON, missing field, invalid data, unknown
  parcel) goes straight to `document.receipts.dlq`; a temporary failure (database or Parcel service down) is tried
  3 times, then goes to the DLQ. The receipt, the "processed" mark and the outgoing event are one transaction.
- **Publishes** `receipt.generated` and `invoice.generated` through a transactional outbox: the event is written in
  the same transaction as the receipt or invoice, and a relay publishes it with the broker's confirm, retrying with a
  growing pause during a broker outage, so nothing is lost. Amounts travel as decimal strings.
- **Internal API** `GET /internal/receipts/{id}/pdf` with the header `X-Internal-Token`: the Notification service
  fetches the PDF to attach it to the receipt email. A user's JWT does not open it.

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | localhost, 5672, `swiftdrop`, dev password (dev profile) | Broker |
| `INTERNAL_API_TOKEN` | `dev-internal-token` (dev profile only) | Shared secret for `/internal/**`; with none set, no internal call is accepted |
| `DOCUMENT_PARCEL_URL` | blank | Parcel service, asked for details the event lacks; blank means never ask |
| `document.messaging.enabled` | `true` | `false` turns the consumer and relay off (the outbox is still written) |

**Pending with Member 4:** the field names of the delivered event (`customerId`, `pricing`, ...) are a proposal,
read in one class, `DeliveredParcelMapper`.

## Verify

```powershell
mvn -f services/document/pom.xml verify
```

The broker tests start a real RabbitMQ and PostgreSQL in Docker (Docker must be running). They publish delivered
events and check the receipt, the `receipt.generated` event, duplicates, the dead-letter paths, the three attempts and
the queue settings.

Tests use a disposable PostgreSQL container. They cover PDF content, concurrent duplicate handling,
snapshot conflicts, decimal normalization, monetary validation, maximum-size amounts, and ownership
authorization. The invoice tests add ownership and role rules, server-computed totals, retry and
conflict handling, one-bill-per-parcel, concurrent duplicates, input validation, the 500-line limit and
multi-page layout. The test-generated PDFs for visual review are `target/qa/receipt-sample.pdf` and
`target/qa/invoice-sample.pdf`. `mvn verify` also enforces 70% line coverage.

OpenPDF release information is at https://github.com/LibrePDF/OpenPDF/releases/tag/2.2.3.
