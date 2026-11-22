# SwiftDrop Merchant Service — Demo Script
> Member 3 | EAD Group Project — Ryzera Team

This walkthrough demonstrates the full Merchant Service lifecycle end-to-end using `curl`.  
Run each step in order after starting the service locally.

---

## Prerequisites

```powershell
# 1. Start backing services (PostgreSQL + RabbitMQ)
docker compose up -d postgres rabbitmq

# 2. Start the merchant service (dev profile)
cd services/merchant
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# Service listens on http://localhost:8081
```

> **Note:** The dev profile pre-creates an admin user and enables the `/api/v1/dev/tokens` shortcut.

---

## Step 1 — Obtain Tokens

```bash
# Get a MERCHANT-role token for a new user
MERCHANT_TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/dev/tokens \
  -H "Content-Type: application/json" \
  -d '{"userId":"demo-merchant-1","role":"MERCHANT"}' | jq -r '.token')

# Get an ADMIN-role token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/dev/tokens \
  -H "Content-Type: application/json" \
  -d '{"userId":"demo-admin-1","role":"ADMIN"}' | jq -r '.token')

echo "MERCHANT_TOKEN=$MERCHANT_TOKEN"
echo "ADMIN_TOKEN=$ADMIN_TOKEN"
```

---

## Step 2 — Register a Merchant (Status: PENDING)

```bash
MERCHANT_ID=$(curl -s -X POST http://localhost:8081/api/v1/merchants \
  -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "businessName": "Demo Shop",
    "businessEmail": "shop@demo.lk",
    "businessPhone": "+94771234567",
    "addressLine": "12 Galle Road",
    "city": "Colombo",
    "postalCode": "00300",
    "country": "LK"
  }' | jq -r '.id')

echo "Merchant ID: $MERCHANT_ID"

# Verify status is PENDING
curl -s http://localhost:8081/api/v1/merchants/$MERCHANT_ID \
  -H "Authorization: Bearer $MERCHANT_TOKEN" | jq '{id, businessName, status}'
# → { "id": "...", "businessName": "Demo Shop", "status": "PENDING" }
```

---

## Step 3 — Admin Approves the Merchant (Status: APPROVED)

```bash
curl -s -X POST http://localhost:8081/api/v1/admin/merchants/$MERCHANT_ID/approve \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '{id, status}'
# → { "id": "...", "status": "APPROVED" }
```

---

## Step 4 — Create a TEST API Key

```bash
KEY_RESPONSE=$(curl -s -X POST http://localhost:8081/api/v1/merchants/$MERCHANT_ID/api-keys \
  -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"environment":"TEST","label":"Demo TEST key"}')

RAW_KEY=$(echo $KEY_RESPONSE | jq -r '.rawKey')
KEY_ID=$(echo $KEY_RESPONSE | jq -r '.id')

echo "Raw Key: $RAW_KEY"
# → sd_test_<prefix>_<secret>
```

---

## Step 5 — Ping to Verify API Key

```bash
curl -s http://localhost:8081/api/v1/merchant-api/ping \
  -H "X-API-Key: $RAW_KEY" | jq .
# → {
#     "merchantId": "...",
#     "businessName": "Demo Shop",
#     "environment": "TEST",
#     "scopes": ["orders:write", "orders:read"]
#   }
```

---

## Step 6 — Create a Sandbox Order (TEST Key → SBX tracking number)

```bash
ORDER=$(curl -s -X POST http://localhost:8081/api/v1/merchant-api/orders \
  -H "X-API-Key: $RAW_KEY" \
  -H "Idempotency-Key: demo-order-001" \
  -H "Content-Type: application/json" \
  -d '{
    "externalRef": "DEMO-001",
    "recipient": {
      "name": "Jane Perera",
      "phone": "+94779876543"
    },
    "pickup": {
      "addressLine": "12 Galle Road",
      "city": "Colombo",
      "postalCode": "00300"
    },
    "dropoff": {
      "addressLine": "45 Temple Street",
      "city": "Kandy",
      "postalCode": "20000"
    },
    "parcel": {
      "weightKg": 1.5,
      "size": "SMALL",
      "description": "Demo parcel"
    }
  }')

echo $ORDER | jq '{trackingNo, status, sandbox}'
# → { "trackingNo": "SBX123456789", "status": "CREATED", "sandbox": true }

TRACKING_NO=$(echo $ORDER | jq -r '.trackingNo')
ORDER_ID=$(echo $ORDER | jq -r '.id')
```

---

## Step 7 — Idempotency Replay (Same Idempotency-Key)

```bash
curl -s -X POST http://localhost:8081/api/v1/merchant-api/orders \
  -H "X-API-Key: $RAW_KEY" \
  -H "Idempotency-Key: demo-order-001" \
  -H "Content-Type: application/json" \
  -d '{ "externalRef": "DEMO-001", "recipient": {"name":"Jane Perera","phone":"+94779876543"}, "pickup": {"addressLine":"12 Galle Road","city":"Colombo","postalCode":"00300"}, "dropoff": {"addressLine":"45 Temple Street","city":"Kandy","postalCode":"20000"}, "parcel": {"weightKg":1.5,"size":"SMALL","description":"Demo parcel"} }' \
  -D - | head -20
# → HTTP/1.1 200 OK
#   Idempotent-Replayed: true
#   (same body as step 6)
```

---

## Step 8 — Advance Sandbox Order Through All States

The sandbox simulator advances status on each POST to the advance endpoint.

```bash
# 1. CREATED → ASSIGNED
curl -s -X POST http://localhost:8081/api/v1/merchant-api/orders/$ORDER_ID/sandbox/advance \
  -H "X-API-Key: $RAW_KEY" | jq '{trackingNo, status}'
# → { "status": "ASSIGNED" }

# 2. ASSIGNED → PICKED_UP
curl -s -X POST http://localhost:8081/api/v1/merchant-api/orders/$ORDER_ID/sandbox/advance \
  -H "X-API-Key: $RAW_KEY" | jq '{trackingNo, status}'
# → { "status": "PICKED_UP" }

# 3. PICKED_UP → IN_TRANSIT
curl -s -X POST http://localhost:8081/api/v1/merchant-api/orders/$ORDER_ID/sandbox/advance \
  -H "X-API-Key: $RAW_KEY" | jq '{trackingNo, status}'
# → { "status": "IN_TRANSIT" }

# 4. IN_TRANSIT → OUT_FOR_DELIVERY
curl -s -X POST http://localhost:8081/api/v1/merchant-api/orders/$ORDER_ID/sandbox/advance \
  -H "X-API-Key: $RAW_KEY" | jq '{trackingNo, status}'
# → { "status": "OUT_FOR_DELIVERY" }

# 5. OUT_FOR_DELIVERY → DELIVERED
curl -s -X POST http://localhost:8081/api/v1/merchant-api/orders/$ORDER_ID/sandbox/advance \
  -H "X-API-Key: $RAW_KEY" | jq '{trackingNo, status}'
# → { "status": "DELIVERED" }
```

---

## Step 9 — Configure Webhook & Send Test Ping

```bash
# Create a webhook endpoint
WEBHOOK=$(curl -s -X POST http://localhost:8081/api/v1/merchants/$MERCHANT_ID/webhooks \
  -H "Authorization: Bearer $MERCHANT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://webhook.site/your-unique-url",
    "events": ["ORDER_STATUS_CHANGED", "ORDER_CREATED"],
    "active": true
  }')

WEBHOOK_ID=$(echo $WEBHOOK | jq -r '.id')
echo "Webhook ID: $WEBHOOK_ID"

# Send a test ping
curl -s -X POST http://localhost:8081/api/v1/merchants/$MERCHANT_ID/webhooks/$WEBHOOK_ID/test \
  -H "Authorization: Bearer $MERCHANT_TOKEN" | jq .
# → { "delivered": true, "httpStatus": 200, "durationMs": 142 }
```

---

## Step 10 — View Webhook Delivery Log

```bash
curl -s "http://localhost:8081/api/v1/merchants/$MERCHANT_ID/webhooks/$WEBHOOK_ID/deliveries?page=0&size=10" \
  -H "Authorization: Bearer $MERCHANT_TOKEN" | jq '{totalElements, content: [.content[] | {event, delivered, httpStatus, attemptedAt}]}'
```

---

## Step 11 — API Key Rotation

```bash
# Rotate the TEST key (old key enters 24-hour grace window)
NEW_KEY_RESPONSE=$(curl -s -X POST http://localhost:8081/api/v1/merchants/$MERCHANT_ID/api-keys/$KEY_ID/rotate \
  -H "Authorization: Bearer $MERCHANT_TOKEN" | jq .)
echo $NEW_KEY_RESPONSE

NEW_RAW_KEY=$(echo $NEW_KEY_RESPONSE | jq -r '.rawKey')

# Old key still works (grace window)
curl -s http://localhost:8081/api/v1/merchant-api/ping \
  -H "X-API-Key: $RAW_KEY" | jq .status
# → 200

# New key also works
curl -s http://localhost:8081/api/v1/merchant-api/ping \
  -H "X-API-Key: $NEW_RAW_KEY" | jq .status
# → 200
```

---

## Step 12 — Unapproved Merchant Blocked (403)

```bash
# Register a second merchant — stays PENDING
PENDING_TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/dev/tokens \
  -H "Content-Type: application/json" \
  -d '{"userId":"demo-pending-1","role":"MERCHANT"}' | jq -r '.token')

PENDING_ID=$(curl -s -X POST http://localhost:8081/api/v1/merchants \
  -H "Authorization: Bearer $PENDING_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"businessName":"Pending Shop","businessEmail":"pending@demo.lk","businessPhone":"+94771111111","addressLine":"1 Main St","city":"Colombo","postalCode":"00100","country":"LK"}' | jq -r '.id')

PENDING_KEY=$(curl -s -X POST http://localhost:8081/api/v1/merchants/$PENDING_ID/api-keys \
  -H "Authorization: Bearer $PENDING_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"environment":"TEST","label":"Pending key"}' | jq -r '.rawKey')

# Attempt to ping — BLOCKED
curl -s http://localhost:8081/api/v1/merchant-api/ping \
  -H "X-API-Key: $PENDING_KEY" | jq .
# → { "code": "MERCHANT_NOT_APPROVED", "status": 403 }
```

---

## Summary

| Step | Action | Expected Result |
|------|--------|----------------|
| 1 | Obtain tokens | JWT for MERCHANT + ADMIN |
| 2 | Register merchant | Status: `PENDING` |
| 3 | Admin approve | Status: `APPROVED` |
| 4 | Create TEST key | `sd_test_...` raw key |
| 5 | Ping | `environment: TEST` |
| 6 | Create sandbox order | `trackingNo: SBX...`, `sandbox: true` |
| 7 | Replay same key | `HTTP 200`, `Idempotent-Replayed: true` |
| 8 | Advance × 5 | `CREATED → DELIVERED` |
| 9 | Webhook test ping | `delivered: true` |
| 10 | View delivery log | Paginated delivery history |
| 11 | Rotate key | Old key grace window, new key active |
| 12 | PENDING merchant | `403 MERCHANT_NOT_APPROVED` |
