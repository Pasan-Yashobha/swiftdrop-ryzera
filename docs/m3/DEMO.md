# SwiftDrop — Member 3 (Merchant Service & Portal) Demo Script

This runbook guides a 5-minute live demonstration of the Merchant Service backend and Merchant Portal frontend for SwiftDrop.

---

## ⏱️ Demo Outline (5 Minutes)

| Segment | Duration | Topic |
|---|---|---|
| **1. Registration & Status** | 1 min | Submit merchant registration via Portal / API; show `PENDING` status. |
| **2. Admin Approval** | 30 sec | Admin approves merchant; show transition to `APPROVED` and outbox event publication. |
| **3. API Credentials & OAuth2** | 1 min | Generate API Key / OAuth Client; fetch 15-min JWT via `POST /oauth/token`. |
| **4. Order Creation & Idempotency** | 1.5 min | Submit order with `Idempotency-Key`; replay request to demonstrate idempotent duplicate rejection. |
| **5. Webhook, Sandbox & Bulk Upload** | 1 min | Test webhook ping with HMAC signature, toggle Sandbox mode, and demonstrate bulk CSV order ingestion. |

---

## 🛠️ Prerequisites & Environment Setup

Ensure the merchant service is running (or Docker compose stack is up):
```powershell
# Run merchant service locally on port 8083 (or default 8080)
cd "services/merchant"
& "C:\Users\Lenovo\.m2\wrapper\dists\apache-maven-3.10.0\2aaba761688b1a6ea5a46e7e5f4d5406ed7a75ee8da95e5e3079863a23461c9b\bin\mvn.cmd" spring-boot:run
```

Environment variables or base URL:
```bash
BASE_URL="http://localhost:8083"
INTERNAL_TOKEN="test-internal-token"
```

---

## 🚀 Step-by-Step Demo Flow

### Step 1: Merchant Registration
Submit a business registration on behalf of a new user:
```bash
curl -s -X POST "$BASE_URL/api/v1/merchants/register" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: u-demo-100" \
  -d '{
    "businessName": "Apex Retailers Ltd",
    "businessRegistrationNumber": "PV-2026-99881",
    "contactPerson": "Kamal Perera",
    "contactPhone": "+94771234567",
    "contactEmail": "ops@apexretail.lk",
    "address": "123 Galle Road, Colombo 03"
  }'
```
*Expected Response (HTTP 201 Created):*
```json
{
  "id": "m-...",
  "ownerUserId": "u-demo-100",
  "businessName": "Apex Retailers Ltd",
  "status": "PENDING"
}
```
*Portal Demo Check:* Navigate to `http://localhost:4200/status` to show the pending onboarding state.

---

### Step 2: Admin Approval
An administrator reviews and approves the application:
```bash
# Save the merchant ID from Step 1
MERCHANT_ID="<MERCHANT_ID_FROM_STEP_1>"

curl -s -X POST "$BASE_URL/api/v1/admin/merchants/$MERCHANT_ID/approve" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: admin-01" \
  -H "X-User-Roles: ADMIN" \
  -d '{
    "rateLimitPerMin": 120
  }'
```
*Expected Response (HTTP 200 OK):*
- Status changes to `APPROVED`.
- `merchant.approved` outbox record is queued in `outbox_events`.
- Identity service is notified to promote user to `MERCHANT` role.

---

### Step 3: API Key & OAuth 2.0 Client Credentials

#### 3A. Issue an OAuth Client (or API Key)
```bash
curl -s -X POST "$BASE_URL/api/v1/merchants/me/oauth-clients" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: u-demo-100" \
  -H "X-User-Roles: MERCHANT" \
  -d '{
    "name": "E-Commerce Backend Client",
    "scopes": ["merchant:orders"]
  }'
```
*Response shows `clientId` and `clientSecret` once. Copy both.*

#### 3B. Exchange Client Credentials for 15-Minute JWT
```bash
CLIENT_ID="<CLIENT_ID>"
CLIENT_SECRET="<CLIENT_SECRET>"

TOKEN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/oauth/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET")

echo $TOKEN_RESPONSE
ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r .access_token)
```
*Response:*
```json
{
  "access_token": "eyJhbGciOi...",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "merchant:orders"
}
```

---

### Step 4: Order Creation & Idempotency Check

#### 4A. Create an Order
```bash
IDEMPOTENCY_KEY="demo-order-key-$(date +%s)"

curl -s -i -X POST "$BASE_URL/api/v1/merchant-api/orders" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "pickup": {
      "name": "Apex Warehouse",
      "phone": "+94771234567",
      "address": "123 Galle Road, Colombo 03"
    },
    "dropoff": {
      "name": "Nimal Silva",
      "phone": "+94777654321",
      "address": "45 Temple Road, Kandy"
    },
    "packageDetails": {
      "weightKg": 2.5,
      "description": "Electronics Component Box"
    }
  }'
```
*Expected Response:* `HTTP 201 Created` with tracking number `SBX-...` (in sandbox) or standard tracking ID.

#### 4B. Replay Same Request (Idempotency Test)
Re-run the exact same curl with the identical `$IDEMPOTENCY_KEY`:
```bash
curl -s -i -X POST "$BASE_URL/api/v1/merchant-api/orders" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -H "Content-Type: application/json" \
  -d '...'
```
*Expected Result:* Returns the previously created order details without duplicating the database entity.

---

### Step 5: Webhook Configuration, Sandbox & Bulk Upload

#### 5A. Configure Webhook & Send Test Ping
```bash
curl -s -X PUT "$BASE_URL/api/v1/merchants/me/webhook" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://webhook.site/#!/view/your-uuid",
    "active": true
  }'

curl -s -X POST "$BASE_URL/api/v1/merchants/me/webhook/test" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```
*Expected Result:* Returns `{ "success": true, "statusCode": 200 }` with HMAC signature headers `X-SwiftDrop-Signature` and `X-SwiftDrop-Timestamp`.

#### 5B. Bulk CSV Upload
```bash
curl -s -X POST "$BASE_URL/api/v1/merchants/me/orders/bulk" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "file=@orders_sample.csv"
```
*Expected Result:* Async ingestion job created with progress polling endpoint.

---

## 🔒 Security & Defense-in-Depth Checks
- **Unapproved Merchant Rejection:**
  Attempting to call `/api/v1/oauth/token` with an unapproved merchant's credentials returns `HTTP 401 / 403`.
- **Secret Zero-Leakage:**
  Raw secrets and API keys are stored hashed (`SHA-256`) and never returned in GET responses or written to system logs.
