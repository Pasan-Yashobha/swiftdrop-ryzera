# Member 3 Implementation Plan: Merchant Service and Merchant Portal

**Author:** Member 3 (Dhanushka Madhushanka)  
**Services:** `services/merchant` (Backend), `frontend/projects/merchant-portal` (Frontend)  
**Database:** `merchant_db` (PostgreSQL)  
**Target Coverage:** ≥ 70% line coverage enforced by JaCoCo  

---

## 1. What Member 3 Owns

### Backend (`services/merchant`, `merchant_db`)
- Registration and approval workflow (`PENDING` -> `APPROVED` / `REJECTED` / `SUSPENDED`).
- API keys (hashed storage via SHA-256, rotation with grace window, revocation).
- OAuth2 client-credentials token endpoint (`/oauth/token`) minting 15-minute JWTs.
- Webhook configuration with AES-GCM encrypted secrets, ping testing, and HMAC signing.
- Per-merchant rate limits (in-memory token bucket + admin overrides).
- Sandbox mode (`SBX-` prefix simulation, independent test orders).
- Bulk CSV order upload with async job progress and deterministic idempotency keys.
- Order read model consuming `parcel.*` events over RabbitMQ with idempotent event handling.

### Frontend (`frontend/projects/merchant-portal`)
- Angular 20 standalone frontend application.
- Merchant registration form, status tracker (`/status`), and profile page.
- Orders dashboard with status filters, search, and pagination.
- API keys & OAuth clients management with copy-once secret reveal modal.
- Webhook configuration, test ping tool, and delivery log viewer.
- Bulk order CSV upload and downloadable error reports.
- Interactive API documentation page with curl/SDK samples.

### Diagrams & Architecture Deliverables
- **Figure 11:** Merchant, Merchant System, and Admin Approval Use Case Diagram.
- **Figure 17:** Sequence Diagram: Merchant API Order Creation & Webhook Delivery.
- **Figure 27:** Activity Diagram: Merchant Approval State Machine.

---

## 2. Key Architecture Decisions & Conventions

| # | Topic | Decision | Notes |
|---|---|---|---|
| **D1** | Initial Status | `PENDING` | Kept consistent with database check constraint and existing enum. |
| **D2** | Frontend Stack | Angular 20 | Matches existing frontend monorepo architecture. |
| **D3** | Token Issuer | Merchant Service | Merchant Service issues client-credentials JWTs signed with shared secret. |
| **D4** | Webhook Retry | 5s, 30s, 2m, 10m, 30m | Aligned with RabbitMQ exchange topology. |
| **D5** | Sandbox Mode | In-Service Simulation | Returns `SBX-` tracking IDs without side effects on physical couriers. |
