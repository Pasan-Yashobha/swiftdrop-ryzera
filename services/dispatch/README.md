# SwiftDrop Dispatch Service

Part of the **SwiftDrop** Parcel Delivery & Courier Management System.
Implemented by **Member 6: W.A.I. Seelaka (234190R) - Group 11 (Ryzera)**.

## Overview
The Dispatch Service manages the unassigned parcel queue, monitors real-time courier workload, and assigns parcels to couriers either manually or automatically via pluggable assignment strategies.

## Technology Stack
- **Java 21** (Eclipse Temurin)
- **Spring Boot 3.4.3 / 3.5.x** (Spring MVC, Spring Data JPA, Spring Security, Spring AMQP)
- **PostgreSQL 16** (`dispatch_db`, owner `dispatch_svc`)
- **Flyway** schema migrations
- **RabbitMQ 3.13** (Topic exchange: `swiftdrop.events`, Dead-letter exchange: `swiftdrop.dlx`)
- **Server-Sent Events (SSE)** for real-time Dispatcher Console updates

## Key Architecture & Design Patterns
- **Strategy Pattern & Template Method**:
  - `AssignmentStrategy` interface
  - `AbstractCapacityAwareStrategy` template method ensuring capacity and availability checks
  - `LeastLoadedStrategy`: Chooses courier with minimum active deliveries; breaks ties with oldest `lastAssignedAt`
  - `ZoneBasedStrategy`: Prioritizes home zone match; cascades to neighbouring zones (10-min cache)
  - `AssignmentStrategyFactory`: Registry dynamically populated with all strategy beans
- **Transactional Outbox Pattern**:
  - Assignment mutations and outbox records saved atomically in one transaction
  - `OutboxRelay` background daemon relays events to `swiftdrop.events`
- **Concurrency Control**:
  - **Same parcel, two couriers**: Partial unique index on active assignments (`ux_assignment_active_parcel`) prevents duplicate assignment -> `409 Conflict`.
  - **Same courier, two parcels**: Pessimistic write lock (`SELECT ... FOR UPDATE`) with `@Version` ensures courier capacity is never exceeded -> `422 Unprocessable Entity`.
  - **Double-click / retry**: `Idempotency-Key` header with 24h cache -> idempotent 201 response.

## REST API Endpoints (`/api/v1/dispatch`)
| Method | Path | Description | Roles |
|---|---|---|---|
| `GET` | `/parcels/unassigned` | Oldest unassigned parcels queue with zone & size filters | `DISPATCHER`, `ADMIN` |
| `GET` | `/couriers/workload` | Courier workload board (active, max, status, zone) | `DISPATCHER`, `ADMIN` |
| `POST` | `/assignments` | Manual parcel assignment | `DISPATCHER`, `ADMIN` |
| `POST` | `/assignments/auto` | Auto-assignment with strategy selection | `DISPATCHER`, `ADMIN` |
| `POST` | `/assignments/{parcelId}/reassign` | Reassign parcel with mandatory audit reason | `DISPATCHER`, `ADMIN` |
| `GET` | `/assignments` | Assignment audit history | `DISPATCHER`, `ADMIN` |
| `GET` | `/stream` | Server-Sent Events stream for live board updates | `DISPATCHER`, `ADMIN` |
