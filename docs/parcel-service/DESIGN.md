# Parcel Service & Customer Application: Design & Architecture (Member 04)

> **Owner:** Member 04  
> **Service:** `services/parcel`  
> **Frontend:** `frontend/projects/customer-app`  
> **Binding Contract:** [`CONTRACT.md`](CONTRACT.md)  
> **Related Specifications:** [`../events/catalogue.md`](../events/catalogue.md), [`../events/envelope.schema.json`](../events/envelope.schema.json)  

---

## 1. Scope & System Role

Member 04 owns the **Parcel Core Domain** and the **Customer Application**:
1. **Parcel Core Domain:** Parcel entity persistence, lifecycle state machine enforcement, immutable status history auditing, tracking number generation with collision retry, and transactional outbox event emission.
2. **Customer Delivery Booking:** Upfront rate estimation with Pricing Service, atomic quote acceptance, and customer-scoped idempotency replay.
3. **Dispatch Integration:** High-throughput `GET /internal/parcels?status=CREATED` endpoint allowing Dispatch Service (Member 02) to pull unassigned parcels.
4. **Customer Delivery Cancellation:** Secure, owner-validated pre-pickup cancellation with transition guards and outbox publication.
5. **Customer Frontend Application:** Modern Angular 20 SPA providing self-service delivery booking, live pricing calculation, parcel details, vertical audit timelines, public tracking, and receipt retrieval.

---

## 2. Microservice Architecture & Topology

```mermaid
flowchart TB
    subgraph CLIENTS["Client Layer"]
        CUST["Customer Browser<br/>(Angular Customer App :4203)"]
        MERCH["Merchant Portal / Backend<br/>(Member 03)"]
        DISP["Dispatch Service<br/>(Member 02 :8084)"]
        COUR["Courier Mobile / Web<br/>(Member 07 :8086)"]
        PUB["Public Visitor<br/>(Tracking Page)"]
    end

    subgraph GW["API Gateway (:8080)"]
        GATEWAY["Spring Cloud Gateway<br/>Rate Limiting, CORS, Path Routing"]
    end

    subgraph SEC["Identity & Auth"]
        IDP["Identity Service (:8081)<br/>RS256 JWT & JWKS Endpoint"]
    end

    subgraph PRICE["Pricing & Quotes"]
        PRICING["Pricing Service (:8085)<br/>Zones, Quotes & Acceptance"]
    end

    subgraph PARCEL["Parcel Service (:8082 - Member 04)"]
        direction TB
        CTRL_CUST["CustomerParcelController<br/>/api/v1/parcels/**"]
        CTRL_INT["InternalParcelController<br/>/internal/parcels/**"]
        CTRL_COUR["CourierStatusController<br/>/api/v1/courier/parcels/**"]
        CTRL_TRK["PublicTrackingController<br/>/api/v1/tracking/**"]
        
        SVC["ParcelService<br/>Domain Orchestrator"]
        SM["ParcelStateMachine<br/>Transition Graph Validator"]
        IDEM["IdempotencyService<br/>Customer-Scoped Replay"]
        TNG["TrackingNumberGenerator<br/>Format: SD26..."]
        OB_SVC["OutboxService<br/>Atomic Event Writer"]
        OB_RELAY["OutboxRelay<br/>Poller & Publisher"]
    end

    subgraph DB["PostgreSQL 16 (:5433)"]
        P_DB[("parcel_db<br/>• parcels<br/>• parcel_status_history<br/>• idempotency_records<br/>• outbox_events")]
    end

    subgraph MQ["RabbitMQ (:5672)"]
        EX{{"swiftdrop.events<br/>Topic Exchange"}}
        Q_TRK[["tracking.q"]]
        Q_MERCH[["merchant.orders.q"]]
        Q_NOTIF[["notification.q"]]
        Q_DOC[["document.receipts.q"]]
    end

    subgraph DOWNSTREAM["Downstream Consumers"]
        DOC["Document Service (:8092)<br/>Receipt Generation"]
        TRK["Tracking Service (:8087)<br/>Redis Read Model & SSE"]
        NOTIF["Notification Service<br/>Email & SMS Alerts"]
    end

    CUST -->|"/api/v1/parcels/**"| GATEWAY
    PUB -->|"/api/v1/tracking/**"| GATEWAY
    COUR -->|"/api/v1/courier/**"| GATEWAY
    GATEWAY --> CTRL_CUST
    GATEWAY --> CTRL_COUR
    GATEWAY --> CTRL_TRK
    
    DISP -->|"GET /internal/parcels<br/>(X-Internal-Token)"| CTRL_INT
    MERCH -->|"POST /internal/parcels<br/>(X-Internal-Token)"| CTRL_INT

    CTRL_CUST --> SVC
    CTRL_INT --> SVC
    CTRL_COUR --> SVC
    CTRL_TRK --> SVC

    CTRL_CUST -.->|"Verify Bearer JWT"| IDP
    CTRL_COUR -.->|"Verify Bearer JWT"| IDP

    SVC -->|"POST /api/v1/quotes/{id}/accept<br/>(X-Internal-Token)"| PRICING
    SVC --> SM
    SVC --> IDEM
    SVC --> TNG
    SVC --> OB_SVC
    
    SVC --> P_DB
    OB_SVC --> P_DB
    IDEM --> P_DB
    
    OB_RELAY --> P_DB
    OB_RELAY -->|"Publish Events"| EX

    EX --> Q_TRK --> TRK
    EX --> Q_DOC --> DOC
    EX --> Q_NOTIF --> NOTIF
```

---

## 3. Domain Model & Class Diagram

The Parcel Service domain architecture isolates controllers, business logic, state transitions, transaction boundary management, and persistence entities.

```mermaid
classDiagram
    class Parcel {
        +UUID id
        +String trackingNumber
        +String customerId
        +UUID merchantId
        +String recipientName
        +String recipientPhone
        +String recipientEmail
        +String pickupAddressLine
        +String pickupCity
        +String dropoffAddressLine
        +String dropoffCity
        +BigDecimal weightKg
        +ParcelSize size
        +DeliveryType deliveryType
        +ParcelStatus status
        +UUID quoteId
        +BigDecimal baseAmount
        +BigDecimal weightAmount
        +BigDecimal sizeAmount
        +BigDecimal totalFee
        +String currency
        +String courierId
        +String failureReason
        +Instant deliveredAt
        +Instant createdAt
        +Instant updatedAt
        +Long version
        +create(...) Parcel$
        +updateStatus(status, courierId, ...) void
    }

    class ParcelStatusHistory {
        +UUID id
        +UUID parcelId
        +int sequenceNumber
        +String fromStatus
        +String toStatus
        +String changedBy
        +String actorRole
        +String notes
        +Instant occurredAt
    }

    class IdempotencyRecord {
        +UUID id
        +String key
        +String requestHash
        +IdempotencyStatus status
        +Integer responseCode
        +String responseBody
        +Instant createdAt
        +Instant expiresAt
    }

    class OutboxEvent {
        +UUID id
        +String aggregateType
        +String aggregateId
        +String eventType
        +String payload
        +OutboxStatus status
        +int retryCount
        +Instant createdAt
        +Instant publishedAt
    }

    class ParcelStatus {
        <<enumeration>>
        CREATED
        ASSIGNED
        PICKED_UP
        IN_TRANSIT
        OUT_FOR_DELIVERY
        DELIVERED
        FAILED
        RESCHEDULED
        RETURNED
        CANCELLED
        +isTerminal() boolean
    }

    class ParcelSize {
        <<enumeration>>
        SMALL
        MEDIUM
        LARGE
    }

    class DeliveryType {
        <<enumeration>>
        STANDARD
        EXPRESS
    }

    class ParcelStateMachine {
        -Map~ParcelStatus, Set~ParcelStatus~~ ALLOWED_TRANSITIONS
        +isAllowed(from, to) boolean
        +validateTransition(from, to) void
    }

    class IdempotencyService {
        -IdempotencyRecordRepository repository
        -Clock clock
        +startOrGetReplay(key, request, responseClass) Optional
        +recordCompleted(key, responseCode, responseBody) void
    }

    class ParcelService {
        -ParcelRepository parcelRepository
        -ParcelStatusHistoryRepository historyRepository
        -ParcelStateMachine stateMachine
        -TrackingNumberGenerator trackingNumberGenerator
        -PricingClient pricingClient
        -OutboxService outboxService
        -IdempotencyService idempotencyService
        +createCustomerParcel(request, customerId, key) CustomerParcelDetailResponse
        +cancelCustomerParcel(parcelId, customerId, isAdmin, reason) CustomerParcelDetailResponse
        +updateCourierStatus(parcelId, request) void
        +getCustomerParcel(parcelId, customerId, isAdmin) CustomerParcelDetailResponse
        +getUnassignedParcels(status) List~UnassignedParcelResponse~
    }

    class CustomerParcelController {
        -ParcelService parcelService
        +createDelivery(key, request) ResponseEntity
        +cancelParcel(id, request) ResponseEntity
        +getParcelDetails(id) ResponseEntity
        +listParcels(status, search, pageable) ResponseEntity
    }

    class InternalParcelController {
        -ParcelService parcelService
        +getUnassignedParcels(status) ResponseEntity
        +getParcelDetails(parcelId) ResponseEntity
        +createParcel(key, request) ResponseEntity
    }

    Parcel --> ParcelStatus
    Parcel --> ParcelSize
    Parcel --> DeliveryType
    ParcelService ..> Parcel : manages
    ParcelService ..> ParcelStatusHistory : records
    ParcelService ..> IdempotencyService : uses
    ParcelService ..> ParcelStateMachine : validates with
    CustomerParcelController ..> ParcelService : delegates
    InternalParcelController ..> ParcelService : delegates
```

---

## 4. Lifecycle State Machine

The parcel lifecycle consists of 10 distinct statuses. The transition graph enforces business logic and prevents illegitimate skips or updates after reaching terminal states (`DELIVERED`, `RETURNED`, `CANCELLED`).

```mermaid
stateDiagram-v2
    [*] --> CREATED : Booking Confirmed (Customer / Merchant)

    CREATED --> ASSIGNED : Dispatcher allocates courier
    CREATED --> CANCELLED : Customer cancels order (Pre-pickup)

    ASSIGNED --> PICKED_UP : Courier collects parcel from sender
    ASSIGNED --> RESCHEDULED : Pickup unsuccessful (sender unavailable)
    ASSIGNED --> CANCELLED : Customer cancels before courier arrives
    ASSIGNED --> FAILED : Pickup permanently abandoned

    PICKED_UP --> IN_TRANSIT : Inbound to sorting hub / trunk transport
    PICKED_UP --> OUT_FOR_DELIVERY : Direct route delivery

    IN_TRANSIT --> OUT_FOR_DELIVERY : Courier departs local delivery hub
    IN_TRANSIT --> RESCHEDULED : Transit exception / delay
    IN_TRANSIT --> FAILED : Package damaged / missing in transit
    IN_TRANSIT --> RETURNED : Return requested by sender

    OUT_FOR_DELIVERY --> DELIVERED : Successful handover + OTP/photo
    OUT_FOR_DELIVERY --> FAILED : Delivery attempt failed (recipient absent)
    OUT_FOR_DELIVERY --> RESCHEDULED : Customer requested next-day delivery
    OUT_FOR_DELIVERY --> RETURNED : Recipient rejected package

    FAILED --> RESCHEDULED : Automatic / dispatcher re-queue
    FAILED --> OUT_FOR_DELIVERY : Courier re-attempts same day
    FAILED --> RETURNED : Return to origin hub

    RESCHEDULED --> ASSIGNED : Re-allocated to courier
    RESCHEDULED --> IN_TRANSIT : Transferred to secondary hub
    RESCHEDULED --> OUT_FOR_DELIVERY : Dispatched on new scheduled date
    RESCHEDULED --> CANCELLED : Customer cancels rescheduled order
    RESCHEDULED --> RETURNED : Return to sender

    DELIVERED --> [*]
    RETURNED --> [*]
    CANCELLED --> [*]

    note right of DELIVERED
        Terminal State
        Generates receipt PDF
        Emits parcel.status.delivered
    end note

    note right of CANCELLED
        Terminal State
        Pre-pickup only
        Emits parcel.cancelled
    end note
```

---

## 5. End-to-End Sequence Diagrams

### 5.1 Customer Upfront Pricing & Atomic Parcel Booking

This sequence illustrates the customer booking flow: estimating the quote, accepting it through Pricing Service, enforcing atomic database updates with customer-scoped idempotency, and emitting the outbox event.

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Customer (Browser)
    participant UI as Angular Customer App
    participant GW as API Gateway
    participant PriceSvc as Pricing Service (:8085)
    participant ParcelCtrl as CustomerParcelController
    participant ParcelSvc as ParcelService
    participant IdemSvc as IdempotencyService
    participant PriceClient as HttpPricingClient
    participant TNG as TrackingNumberGenerator
    participant DB as PostgreSQL (parcel_db)
    participant Relay as OutboxRelay
    participant MQ as RabbitMQ (swiftdrop.events)

    Note over Customer, UI: Step 1: Upfront Pricing Estimate
    Customer->>UI: Selects pickup & dropoff cities, size, weight
    UI->>GW: POST /api/v1/quotes
    GW->>PriceSvc: Calculate rate
    PriceSvc-->>UI: 200 OK (Quote ID, fee breakdown, expiresAt)
    UI-->>Customer: Displays fee preview (Base, Weight, Express, Total)

    Note over Customer, UI: Step 2: Book Delivery with Idempotency Key
    Customer->>UI: Enters recipient details and clicks "Book Delivery"
    UI->>UI: Generates client UUID Idempotency-Key
    UI->>GW: POST /api/v1/parcels [Header: Idempotency-Key, Bearer JWT]
    GW->>ParcelCtrl: Route with authenticated customerId

    Note over ParcelCtrl, ParcelSvc: Step 3: Transaction Boundary & Replay Check
    ParcelCtrl->>ParcelSvc: createCustomerParcel(request, customerId, rawKey)
    ParcelSvc->>IdemSvc: startOrGetReplay("customer:{id}:{key}", request)
    IdemSvc->>DB: Check idempotency_records
    alt Key already COMPLETED
        IdemSvc-->>ParcelSvc: Return cached CustomerParcelDetailResponse
        ParcelSvc-->>ParcelCtrl: Cached response
        ParcelCtrl-->>UI: 201 Created (Cached replay)
    else Key IN_PROGRESS
        IdemSvc-->>ParcelSvc: Throw 409 Conflict (IDEMPOTENCY_KEY_IN_USE)
    else New Key
        IdemSvc->>DB: INSERT IN_PROGRESS record
    end

    Note over ParcelSvc, PriceClient: Step 4: Accept Quote via Pricing Service
    ParcelSvc->>PriceClient: acceptQuote(quoteId) [Header: X-Internal-Token]
    PriceClient->>PriceSvc: POST /api/v1/quotes/{id}/accept
    PriceSvc-->>PriceClient: 200 OK (PricingSnapshot)
    PriceClient-->>ParcelSvc: Verified fee breakdown

    Note over ParcelSvc, DB: Step 5: Atomic Domain Transaction Commit
    ParcelSvc->>TNG: generate() -> "SD260481923"
    ParcelSvc->>DB: INSERT into parcels (status = 'CREATED')
    ParcelSvc->>DB: INSERT into parcel_status_history (seq = 1, 'CUSTOMER')
    ParcelSvc->>DB: INSERT into outbox_events ('parcel.created')
    ParcelSvc->>IdemSvc: recordCompleted(scopedKey, 201, responseBody)
    IdemSvc->>DB: UPDATE idempotency_records SET status = 'COMPLETED'
    
    ParcelSvc-->>ParcelCtrl: CustomerParcelDetailResponse
    ParcelCtrl-->>UI: 201 Created [Location: /api/v1/parcels/{id}]
    UI-->>Customer: Displays Delivery Confirmation with Tracking No

    Note over Relay, MQ: Step 6: Asynchronous Outbox Relay
    loop Every 1000ms
        Relay->>DB: SELECT * FROM outbox_events WHERE status = 'PENDING'
        Relay->>MQ: BasicPublish 'parcel.created' (swiftdrop.events)
        Relay->>DB: UPDATE outbox_events SET status = 'PUBLISHED'
    end
```

---

### 5.2 Customer Delivery Cancellation

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Customer (Browser)
    participant UI as Angular Customer App
    participant ParcelCtrl as CustomerParcelController
    participant ParcelSvc as ParcelService
    participant SM as ParcelStateMachine
    participant DB as PostgreSQL (parcel_db)
    participant Relay as OutboxRelay
    participant MQ as RabbitMQ (swiftdrop.events)

    Customer->>UI: Clicks "Cancel Delivery" on Parcel Details page
    UI-->>Customer: Opens confirmation dialog (optional reason input)
    Customer->>UI: Enters reason & clicks "Confirm Cancellation"
    UI->>ParcelCtrl: POST /api/v1/parcels/{id}/cancel [Bearer JWT]
    
    ParcelCtrl->>ParcelSvc: cancelCustomerParcel(id, customerId, false, reason)
    ParcelSvc->>DB: SELECT * FROM parcels WHERE id = :id
    
    alt Customer is not owner
        ParcelSvc-->>ParcelCtrl: Throw 403 Forbidden
        ParcelCtrl-->>UI: 403 Forbidden (Error alert displayed)
    else Status is not in [CREATED, ASSIGNED, RESCHEDULED]
        ParcelSvc->>SM: validateTransition(fromStatus, CANCELLED)
        SM-->>ParcelSvc: Throw 409 Conflict (INVALID_STATE)
        ParcelSvc-->>ParcelCtrl: Throw 409 Conflict
        ParcelCtrl-->>UI: 409 Conflict (Refreshes parcel state)
    else Valid Pre-pickup Cancellation
        ParcelSvc->>DB: UPDATE parcels SET status = 'CANCELLED'
        ParcelSvc->>DB: INSERT parcel_status_history (seq = N+1, 'CUSTOMER', reason)
        ParcelSvc->>DB: INSERT outbox_events ('parcel.cancelled')
        ParcelSvc-->>ParcelCtrl: Updated CustomerParcelDetailResponse
        ParcelCtrl-->>UI: 200 OK (Refreshed parcel details)
        UI-->>Customer: Displays "Delivery has been successfully cancelled"
        
        Relay->>DB: Poll pending events
        Relay->>MQ: BasicPublish 'parcel.cancelled' (swiftdrop.events)
        Relay->>DB: UPDATE outbox_events SET status = 'PUBLISHED'
    end
```

---

## 6. PostgreSQL Schema Design

The `parcel_db` schema adheres to strict data integrity rules, check constraints, foreign keys, and optimistic concurrency locking:

* `parcels`: Primary operational entity. Stores tracking number (unique), customer/merchant IDs, pickup/dropoff addresses, physical specs (weight, size, priority), accepted fee breakdown, courier assignment, failure reasons, and timestamps.
* `parcel_status_history`: Append-only audit trail. Sequence numbers are strictly incremented per parcel ID. Indexed on `parcel_id` and `sequence_number`.
* `idempotency_records`: Records hashed requests by scoped key (`customer:{id}:{key}` or `merchant:{key}`), request SHA-256 hash, execution status (`IN_PROGRESS`, `COMPLETED`), and serialized response body.
* `outbox_events`: Transactional outbox table. Stores `aggregate_id`, `event_type`, JSON payload, retry counts, and publication timestamps.

---

## 7. Testing & Quality Verification

Member 04 maintains 100% passing automated test suites across all backend and frontend layers:

1. **Parcel Service Backend:**
   * **Test Count:** **128 tests run, 0 failures, 0 errors, 0 skipped**.
   * **State Transition Coverage:** `ParcelStateMachineTest` exhaustively tests all valid combinations and invalid/terminal rejections (40 tests).
   * **Idempotency & Concurrency:** Verified atomic completion, scoped key collision prevention, and payload mismatch rejection.
   * **Integration Tests:** `ParcelEventFlowIntegrationTest` verifies transactional outbox persistence and relay formatting.
2. **Customer Frontend Application:**
   * **Test Count:** **80 tests run, 0 failures, 0 errors** in Chrome Headless.
   * **Components Verified:** `BookDeliveryPage`, `ParcelDetailsPage`, `DeliveryHistoryPage`, `TrackParcelPage`, `SignupPage`, `LoginPage`.
   * **Production Build:** Passes with initial bundle size of **300.14 kB**, well within the 1 MB budget.

---

## 8. Integration Status & Dependencies

* **Pricing Service (:8085 - Member 05):** Fully integrated for active zones fetching and synchronous quote acceptance via `HttpPricingClient`.
* **Identity Service (:8081 - Member 01):** Integrated for RS256 JWT bearer token verification and public keys (`/.well-known/jwks.json`).
* **Dispatch Service (:8084 - Member 02):** Implemented `GET /internal/parcels?status=CREATED`. Dispatch Service needs to include `X-Internal-Token: dev-internal-token` in its `ParcelClient` to pull live parcels.
* **Courier Service (:8086 - Member 07):** Updates parcel delivery statuses and proof of delivery via `/api/v1/courier/parcels/{parcelId}/status`.
* **Document Service (:8092 - Member 05):** Client proxy route `/api/v1/receipts` configured. Live testing requires starting the Document Service container.
