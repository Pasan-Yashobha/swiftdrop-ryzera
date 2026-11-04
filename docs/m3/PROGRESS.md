# Member 3 — Progress Log

> Branch: `feature/member3-merchant`  
> Owner: Member 3 (Dhanushka Madhushanka)  
> Services: `services/merchant`, `frontend/projects/merchant-portal`  

---

## Step 0 — Repo Reconnaissance & Architecture Review ✅ COMPLETE

**Date:** 2026-11-04  

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
