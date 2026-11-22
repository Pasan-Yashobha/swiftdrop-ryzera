# Parcel Service and Customer Application: Contract Specification (Member 04)

> **Owner:** Member 04  
> **Service:** `services/parcel` (Spring Boot 3.5.16, Java 21)  
> **Frontend:** `frontend/projects/customer-app` (Angular 20 Standalone)  
> **Database:** PostgreSQL (`parcel_db` on host port 5433, owner: `parcel_svc`)  
> **Messaging:** RabbitMQ exchange `swiftdrop.events` (Topic) via Transactional Outbox  
> **Companion Document:** [`DESIGN.md`](DESIGN.md)  

---

## 1. Scope & Ownership

Member 04 is responsible for end-to-end parcel booking, lifecycle management, status auditing, and customer interaction:
1. **Parcel Lifecycle Management:** Complete state machine transitions across 10 lifecycle states, enforcing valid transition rules, courier attribution, and terminal state immutability.
2. **Customer Delivery Booking:** Upfront pricing quote validation, acceptance via Pricing Service (port 8085), and atomic parcel creation.
3. **Customer-Scoped Idempotency:** Prevention of duplicate bookings and phantom orders across network retries using client-supplied `Idempotency-Key` headers scoped by customer identity.
4. **Status Audit Trail:** Immutable, sequence-audited history table logging every transition with actor role, operator ID, timestamp, and transition notes.
5. **Transactional Outbox Engine:** Guaranteed at-least-once event publication to RabbitMQ topic exchange `swiftdrop.events` for all parcel mutations (`parcel.created`, `parcel.assigned`, `parcel.cancelled`, `parcel.status.*`).
6. **Dispatch Queue Integration:** High-performance internal query endpoint (`GET /internal/parcels?status=CREATED`) enabling Dispatch Service (Member 02) to pull unassigned parcels.
7. **Customer Parcel Cancellation:** Pre-pickup customer cancellation (`POST /api/v1/parcels/{id}/cancel`) with state validation and outbox event emission.
8. **Customer Web Application:** Modern Angular 20 application with standalone components, signals, Sri Lankan phone normalization, reactive booking, pricing fee preview, tracking, delivery history, and receipt download.

---

## 2. Tech Stack & Conventions

* **Language & Runtime:** Java 21, Spring Boot 3.5.16
* **Package Root:** `lk.swiftdrop.parcel`
* **Artifact:** `lk.swiftdrop:parcel-service`
* **Ports:**
  * Container port: `8080`
  * Local host port: `8082` (Dev runtime, Gateway routes `/api/v1/parcels/**` and `/api/v1/tracking/**` here)
* **Security:**
  * Customer & Courier API: Spring Security OAuth2 Resource Server validating RS256 JWTs issued by Identity Service (`http://localhost:8081/.well-known/jwks.json`).
  * Internal APIs (`/internal/**`): Secured with `InternalTokenFilter` enforcing `X-Internal-Token: dev-internal-token`.
* **Standard Error Response:**
  ```json
  {
    "timestamp": "2026-11-18T07:15:00Z",
    "status": 409,
    "error": "Conflict",
    "code": "INVALID_STATE",
    "message": "Invalid parcel state transition from 'PICKED_UP' to 'CANCELLED'",
    "path": "/api/v1/parcels/11111111-2222-3333-4444-555555555555/cancel",
    "correlationId": "c8a412b3-90ef-4f51-b847-19d426a8d672"
  }
  ```

---

## 3. REST API Contracts

### 3.1 Customer Parcel Endpoints (`/api/v1/parcels`)
All customer endpoints require `Authorization: Bearer <JWT>` with role `ROLE_CUSTOMER` or `ROLE_ADMIN`.

#### `POST /api/v1/parcels`
Book a new customer parcel delivery with an accepted pricing quote.

* **Headers:**
  * `Authorization: Bearer <JWT>` (Required)
  * `Idempotency-Key: <UUID>` (Optional, max 120 chars; enables idempotent replay)
* **Request Body:**
  ```json
  {
    "recipient": {
      "name": "Nimal Perera",
      "phone": "+94771234567",
      "email": "nimal@example.com"
    },
    "pickup": {
      "addressLine": "123 Galle Road",
      "city": "Colombo",
      "postalCode": "00300"
    },
    "dropoff": {
      "addressLine": "45 Peradeniya Road",
      "city": "Kandy",
      "postalCode": "20000"
    },
    "weightKg": 2.50,
    "size": "MEDIUM",
    "description": "Educational Textbooks",
    "deliveryType": "STANDARD",
    "notes": "Fragile, please handle with care",
    "quoteId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"
  }
  ```
* **Success Response:** `201 Created` with `Location: /api/v1/parcels/{id}`
  ```json
  {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "trackingNumber": "SD260481923",
    "status": "CREATED",
    "recipient": { "name": "Nimal Perera", "phone": "+94771234567", "email": "nimal@example.com" },
    "pickup": { "addressLine": "123 Galle Road", "city": "Colombo", "postalCode": "00300" },
    "dropoff": { "addressLine": "45 Peradeniya Road", "city": "Kandy", "postalCode": "20000" },
    "weightKg": 2.50,
    "size": "MEDIUM",
    "deliveryType": "STANDARD",
    "description": "Educational Textbooks",
    "notes": "Fragile, please handle with care",
    "pricing": {
      "quoteId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "baseAmount": 300.00,
      "weightAmount": 100.00,
      "sizeAmount": 50.00,
      "expressAmount": 0.00,
      "discountAmount": 0.00,
      "totalFee": 450.00,
      "currency": "LKR"
    },
    "courierId": null,
    "failureReason": null,
    "deliveredAt": null,
    "createdAt": "2026-11-18T07:00:00Z",
    "updatedAt": "2026-11-18T07:00:00Z",
    "history": [
      {
        "sequenceNumber": 1,
        "fromStatus": null,
        "toStatus": "CREATED",
        "changedBy": "5422c836-a463-4164-a417-c15159a7d597",
        "actorRole": "CUSTOMER",
        "notes": "Initial delivery booking created",
        "occurredAt": "2026-11-18T07:00:00Z"
      }
    ]
  }
  ```
* **Error Responses:**
  * `400 Bad Request`: Validation failure or idempotency key > 120 characters.
  * `409 Conflict`: `IDEMPOTENCY_KEY_IN_USE` (concurrent in-flight request).
  * `422 Unprocessable Entity`: `IDEMPOTENCY_PAYLOAD_MISMATCH` (reusing same key with different payload).

#### `GET /api/v1/parcels/{id}`
Fetch parcel details and status history for the authenticated owner or admin.

* **Success Response:** `200 OK` (returns `CustomerParcelDetailResponse` as shown above).
* **Errors:** `401 Unauthorized`, `403 Forbidden` (if customer does not own parcel), `404 Not Found`.

#### `GET /api/v1/parcels`
List parcels for the authenticated customer with pagination, status filtering, and search.

* **Query Parameters:**
  * `status`: Optional `ParcelStatus` filter (`CREATED`, `ASSIGNED`, `IN_TRANSIT`, `DELIVERED`, etc.)
  * `search`: Optional string (matches tracking number or recipient name)
  * `page`: Zero-based page index (default: `0`)
  * `size`: Page size (default: `20`)
  * `sort`: Sort specification (default: `createdAt,desc`)
* **Success Response:** `200 OK` with `PageResponse<CustomerParcelSummaryResponse>`

#### `POST /api/v1/parcels/{id}/cancel`
Cancel an eligible pre-pickup delivery order.

* **Pre-conditions:**
  * User must be the authenticated owner (or `ROLE_ADMIN`).
  * Current parcel status must be in eligible pre-pickup state: `CREATED`, `ASSIGNED`, or `RESCHEDULED`.
* **Request Body (Optional):**
  ```json
  {
    "reason": "Changed delivery plans"
  }
  ```
* **Success Response:** `200 OK` with updated `CustomerParcelDetailResponse` (`status: "CANCELLED"`, history appended, outbox event `parcel.cancelled` queued).
* **Errors:**
  * `403 Forbidden`: Authenticated user is not the owner.
  * `404 Not Found`: Parcel ID does not exist.
  * `409 Conflict`: `INVALID_STATE` if parcel is already `CANCELLED` or already `PICKED_UP`/`IN_TRANSIT`/`DELIVERED`.

---

### 3.2 Internal Microservice Endpoints (`/internal/parcels`)
All internal endpoints require `X-Internal-Token: dev-internal-token`.

#### `GET /internal/parcels?status=CREATED`
Query unassigned or rescheduled parcels for the Dispatch Service.

* **Headers:** `X-Internal-Token: dev-internal-token`
* **Query Parameters:** `status` (Optional, defaults to `CREATED`; supports `RESCHEDULED`)
* **Success Response:** `200 OK` with JSON array:
  ```json
  [
    {
      "parcelId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
      "trackingNumber": "SD260481923",
      "pickupZone": null,
      "pickupArea": "Colombo",
      "size": "MEDIUM",
      "weight": 2.50,
      "createdAt": "2026-11-18T07:00:00Z"
    }
  ]
  ```

#### `GET /internal/parcels/{parcelId}`
Fetch internal parcel pricing and tracking details.

* **Success Response:** `200 OK`
  ```json
  {
    "parcelId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "trackingNo": "SD260481923",
    "customerId": "5422c836-a463-4164-a417-c15159a7d597",
    "pricing": {
      "baseAmount": 300.00,
      "weightAmount": 100.00,
      "sizeAmount": 50.00,
      "expressAmount": 0.00,
      "discountAmount": 0.00,
      "totalAmount": 450.00
    },
    "currency": "LKR"
  }
  ```

#### `POST /internal/parcels`
Merchant order creation (called by Merchant Service, Member 03).

* **Headers:** `X-Internal-Token: dev-internal-token`, `Idempotency-Key: <key>`
* **Success Response:** `201 Created` with `InternalParcelBookingResponse` (`parcelId`, `trackingNo`, `status`, `fee`).

---

### 3.3 Courier Status Update Endpoint (`/api/v1/courier/parcels/{parcelId}/status`)
Secured with JWT authentication (`ROLE_COURIER`, `ROLE_ADMIN`).

* **Request Body:**
  ```json
  {
    "courierId": "CR-1002",
    "status": "DELIVERED",
    "recipientName": "Nimal Perera",
    "photoObjectKey": "proofs/2026/11/photo-01.jpg",
    "otp": "491823",
    "failureReason": null,
    "note": "Package received directly by customer"
  }
  ```
* **Success Response:** `204 No Content`
* **Validations:** Enforces assigned courier identity, valid state machine transition, terminal state rules, appends status history, and emits corresponding outbox event.

---

### 3.4 Public Tracking Endpoint (`/api/v1/tracking/{trackingNo}`)
Unauthenticated public endpoint.

* **Success Response:** `200 OK`
  ```json
  {
    "trackingNumber": "SD260481923",
    "status": "IN_TRANSIT",
    "pickupCity": "Colombo",
    "dropoffCity": "Kandy",
    "createdAt": "2026-11-18T07:00:00Z",
    "deliveredAt": null,
    "milestones": [
      { "status": "CREATED", "occurredAt": "2026-11-18T07:00:00Z", "notes": "Initial booking" },
      { "status": "ASSIGNED", "occurredAt": "2026-11-18T07:30:00Z", "notes": "Assigned to courier CR-1002" },
      { "status": "PICKED_UP", "occurredAt": "2026-11-18T08:15:00Z", "notes": "Courier picked up parcel" },
      { "status": "IN_TRANSIT", "occurredAt": "2026-11-18T09:00:00Z", "notes": "Dispatched to Kandy sorting facility" }
    ]
  }
  ```

---

## 4. RabbitMQ Outbox Event Contracts

Events are published to topic exchange `swiftdrop.events` wrapped in the standard SwiftDrop envelope (`eventId`, `eventType`, `version`, `occurredAt`, `producer`, `correlationId`, `aggregateId`, `data`).

| Event Type | Trigger | Aggregate ID | Payload (`data`) Fields |
| :--- | :--- | :--- | :--- |
| `parcel.created` | Customer or merchant creates parcel | `parcelId` | `parcelId, trackingNo, customerId, merchantId?` |
| `parcel.assigned` | Parcel assigned to courier | `parcelId` | `parcelId, trackingNo, courierId, merchantId?` |
| `parcel.cancelled` | Pre-pickup cancellation | `parcelId` | `parcelId, trackingNo, customerId, merchantId?, reason?` |
| `parcel.status.picked_up` | Courier collects package | `parcelId` | `parcelId, trackingNo, fromStatus, toStatus, courierId, customerId, merchantId?` |
| `parcel.status.in_transit` | Hub sortation / trunk movement | `parcelId` | `parcelId, trackingNo, fromStatus, toStatus, courierId, customerId, merchantId?` |
| `parcel.status.out_for_delivery` | Courier begins final delivery | `parcelId` | `parcelId, trackingNo, fromStatus, toStatus, courierId, customerId, merchantId?` |
| `parcel.status.delivered` | Successful handover with proof | `parcelId` | `parcelId, trackingNo, fromStatus, toStatus, courierId, customerId, merchantId?, deliveredAt, pricing, currency` |
| `parcel.status.failed` | Delivery attempt failed | `parcelId` | `parcelId, trackingNo, fromStatus, toStatus, courierId, customerId, merchantId?, failureReason` |
| `parcel.status.rescheduled` | Re-delivery scheduled | `parcelId` | `parcelId, trackingNo, fromStatus, toStatus, courierId, customerId, merchantId?` |
| `parcel.status.returned` | Returned to sender/hub | `parcelId` | `parcelId, trackingNo, fromStatus, toStatus, courierId, customerId, merchantId?` |
