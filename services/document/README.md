# SwiftDrop Document Service

Java 21, Spring Boot 3.5.16, PostgreSQL, Flyway, and OpenPDF 2.2.3.

## First receipt milestone

The service generates an A5 delivery receipt from a confirmed delivered-parcel snapshot, including
the itemized charges and discount. It preserves those amounts instead of recalculating current rates.
One immutable receipt is stored per parcel. Retrying the same snapshot returns the same receipt ID.
A different snapshot for an existing parcel returns 409. Concurrent duplicate requests are handled
by PostgreSQL's unique parcel constraint and one transaction for metadata and PDF bytes.

This milestone stores PDFs in PostgreSQL BYTEA. Private MinIO/S3 storage, receipt generation from
RabbitMQ, receipt.generated publication, merchant invoices, and the Angular receipt viewer are
subsequent integration steps. The checkout currently has no Parcel implementation or confirmed
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

The service runs on port 8087. Health is available at `http://localhost:8087/actuator/health`.

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
$admin = Invoke-RestMethod -Method Post -Uri http://localhost:8086/api/v1/dev/tokens `
  -ContentType application/json -Body '{"sub":"document-demo-admin","role":"ADMIN"}'
$headers = @{ Authorization = "Bearer " + $admin.accessToken }
# Save the example snapshot above as receipt.json in your local working directory.
$receipt = Invoke-RestMethod -Method Post -Uri http://localhost:8087/api/v1/dev/receipts `
  -Headers $headers -ContentType application/json -Body (Get-Content receipt.json -Raw)
Invoke-WebRequest -Uri ("http://localhost:8087/api/v1/receipts/" + $receipt.id + "/download") `
  -Headers $headers -OutFile "$env:TEMP/swiftdrop-receipt.pdf"
```

## Verify

```powershell
mvn -f services/document/pom.xml test
```

Tests use a disposable PostgreSQL container. They cover PDF content, concurrent duplicate handling,
snapshot conflicts, decimal normalization, monetary validation, maximum-size amounts, and ownership
authorization. The test-generated PDF for visual review is `target/qa/receipt-sample.pdf`.

OpenPDF release information is at https://github.com/LibrePDF/OpenPDF/releases/tag/2.2.3.
