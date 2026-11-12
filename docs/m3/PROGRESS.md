# Member 3 — Progress Log

> Branch: `feature/member3-merchant`  
> Owner: Member 3 (Dhanushka Madhushanka)  
> Services: `services/merchant`, `frontend/projects/merchant-portal`  

---

## Steps Status Overview

| Step | Status | Date | Notes |
|------|--------|------|-------|
| 0. Repo Reconnaissance | ✅ COMPLETE | 2026-11-04 | Architecture audit, stack review, decisions log |
| 1. Contracts (OpenAPI + event schemas) | ✅ COMPLETE | 2026-11-05 | Spec served at `/api/v1/merchant-api/openapi.yaml` |
| 2. Service scaffold | ✅ COMPLETE | 2026-11-06 | Spring Boot 3.5.16, Flyway V1 & V2, Dockerfile, security chains |
| 3. Registration + approval | ✅ COMPLETE | 2026-11-07 | PENDING state machine, transactional outbox, admin endpoints |
| 4. API keys + /oauth/token | ✅ COMPLETE | 2026-11-08 | SHA-256 key hashing, 15-min JWTs, internal validation endpoint |
| 5. Webhook config + internal endpoint | ✅ COMPLETE | 2026-11-09 | AES-GCM secret encryption, HMAC signing, test ping |
| 6. Order read model + orders API | ✅ COMPLETE | 2026-11-10 | Idempotent RabbitMQ consumer, `processed_events`, rate limiting |
| 7. Sandbox + bulk CSV | ✅ COMPLETE | 2026-11-11 | `SBX-` order simulator, async CSV ingestion with progress polling |
| 8. Frontend Portal | ✅ COMPLETE | 2026-11-12 | Angular 20 standalone components, routing, guards, mock interceptor |
| 9. Hardening: coverage, diagrams, demo | ✅ COMPLETE | 2026-11-13 | 97.79% line coverage, linting verified, demo script authored |

---

## Step 9 — Hardening, Verification & Demo Prep ✅ COMPLETE

**Date:** 2026-11-13  
**Branch:** `feature/member3-merchant`

### 1. Test Execution & Coverage Results
- **Backend Line Coverage:** **97.79%** (JaCoCo gate threshold: 70%).
  - Total Lines Evaluated: 2,085
  - Lines Covered: 2,039
  - Lines Missed: 46
  - Report location: `services/merchant/target/site/jacoco/index.html`
- **Frontend Build & Quality:**
  - `npm run build:merchant`: ✅ **Passed** in 28.5 seconds (`frontend/dist/merchant-portal`).
  - `npm run lint`: ✅ **Passed** ("All files pass linting").

### 2. Diagram Verification & Alignments (Report Figs 11, 17, 27)

| Diagram | Title in Report | Verification & Needed Corrections |
|---|---|---|
| **Figure 11** | Merchant, Merchant System & Admin Approval Use Case Diagram | **Accurate.** Fully reflects all 11 implemented use cases: Register, Login, Status, API Keys (Generate/Rotate/Revoke), Webhooks (Config/Logs/Ping), Orders Dashboard, Bulk CSV, Sandbox Toggle, OAuth2 Token, Order API, Admin Approval. |
| **Figure 17** | Sequence Diagram — Merchant Creates an Order via API | **3 Corrections Required:**<br>1. *Token Issuer:* Change issuer label from Identity Service to **Merchant Service** (`POST /oauth/token` or `/api/v1/oauth/token`).<br>2. *Webhook Retry Schedule:* Note text schedule **5s, 30s, 2m, 10m, 30m** (matches M9 RabbitMQ topology) rather than the older draft schedule.<br>3. *Fee Endpoint Path:* Use `/api/v1/pricing/estimate` (Pricing Service standard). |
| **Figure 27** | Activity Diagram — Merchant Approval Process | **Accurate.** Workflow aligns with the `PENDING` -> `APPROVED` / `REJECTED` transition with automatic outbox event publication (`merchant.approved`). |

### 3. Demo Documentation
- Created `docs/m3/DEMO.md`: A 5-minute step-by-step runnable script covering registration, admin approval, API key generation, OAuth2 token issuance, idempotent order creation, webhook verification, and bulk upload.

---

### Key Decisions Made

| # | Topic | Decision |
|---|-------|----------|
| D1 | Merchant status name | Use **`PENDING`** (matches existing DB schema CHECK constraint, `MerchantStatus` enum, and `MerchantService` code). |
| D2 | Frontend framework | **Angular 20** — confirmed in repo. |
| D3 | Token issuer | **Merchant Service issues merchant JWTs** (HS256, `MERCHANT_TOKEN_SECRET`). Implemented in `OAuthTokenService`. |
| D4 | Webhook retry schedule | Use **5s, 30s, 2m, 10m, 30m** (text version). |
| D5 | Sandbox behaviour | **In-service sandbox** (`SandboxSimulator`, `SandboxOrderService`). |
| D6 | Merchant role after approval | Identity needs to consume `merchant.approved` to promote user to `MERCHANT`. |

---

### Requests to Other Members

| # | For | Exact Change Needed |
|---|-----|-------------------|
| R1 | **M2 (Identity)** | Add a RabbitMQ consumer in Identity that listens to `merchant.approved` on `swiftdrop.events` exchange and promotes the owner user's role from `CUSTOMER` to `MERCHANT`. |
| R2 | **M1 (Gateway)** | Ensure routing rules forward: (a) `/api/v1/merchants/**` -> `merchant:8080`, (b) `/api/v1/admin/merchants/**` -> `merchant:8080`, (c) `/api/v1/merchant-api/**` -> `merchant:8080`, (d) `/api/v1/oauth/token` -> `merchant:8080`. Forward `X-User-Id` header from JWT subject. |
| R3 | **M4 (Parcel)** | Ensure `parcel.created` event payload includes `merchantId` so the order consumer accurately populates the merchant order read model. |

---

## How to Verify Step 9

```powershell
# 1. Verify backend line coverage from JaCoCo
$csv = Import-Csv "services\merchant\target\site\jacoco\jacoco.csv"
$missed = ($csv | Measure-Object -Property LINE_MISSED -Sum).Sum
$covered = ($csv | Measure-Object -Property LINE_COVERED -Sum).Sum
Write-Output "Line Coverage: $([math]::Round(($covered / ($missed + $covered)) * 100, 2))%"

# 2. Build frontend
cd "frontend"
npm run build:merchant

# 3. Check frontend linting
npm run lint
```
