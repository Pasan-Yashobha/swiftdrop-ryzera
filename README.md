# SwiftDrop: Parcel Delivery & Courier Management System

Stack: Angular, Java Spring Boot, PostgreSQL (AWS RDS), RabbitMQ, Docker, Kubernetes, AWS CDK or OpenTofu, Actuator + Prometheus + Grafana.
Local development uses Docker Compose (Postgres runs in a container); the cloud deployment uses RDS.

Enterprise application development group project (10 members).

## Quick start (local platform stack)

```bash
docker compose up -d          # Postgres, RabbitMQ, Redis, Mailpit, Identity, Merchant (no .env needed)
docker compose ps             # everything should become "healthy"
```

Then start the frontend dev server (from `frontend/`):

```bash
npm start                     # Angular dev server on http://localhost:4200 with API proxy
```

Optional extras:

```bash
docker compose --profile observability up -d   # Prometheus + Grafana
```

## Demo login credentials

All demo accounts share the default password: **`Password123!`**

| Role | Email | Password | Landing page |
|---|---|---|---|
| Admin | `admin@swiftdrop.com` | `Password123!` | `/welcome` |
| Merchant | `merchant@swiftdrop.com` | `Password123!` | `/dashboard` |
| Courier | `courier@swiftdrop.com` | `Password123!` | `/welcome` |
| Customer | `customer@swiftdrop.com` | `Password123!` | `/welcome` |

> These accounts are seeded automatically by Flyway migrations V2 & V3 on first startup.

## Local service URLs

| Service | URL | Notes |
|---|---|---|
| **Frontend (Angular)** | http://localhost:4200 | Proxies `/api/v1/auth` → 8081, `/api` → 8083 |
| **Identity Service** | http://localhost:8081 | Auth, JWT, user management |
| **Merchant Service** | http://localhost:8083 | Merchant & parcel APIs |
| **RabbitMQ UI** | http://localhost:15672 | user/pass from `.env` (default: `swiftdrop` / `dev_rabbit_pw`) |
| **Mailpit inbox** | http://localhost:8025 | Captures all outbound emails in dev |
| **PostgreSQL** | localhost:**5433** | Host port 5433 (not 5432) to avoid conflicts |
| **Prometheus** | http://localhost:9090 | `observability` profile only |
| **Grafana** | http://localhost:3001 | `observability` profile only |

## Repository layout

```
services/      one folder per microservice (Spring Boot, Maven)
frontend/      Angular workspace (merchant-portal and shared libraries)
infra/         local Postgres init, Prometheus, Grafana config, plus cloud/ and k8s/
templates/     Dockerfile template for services
.github/       CI workflows
docs/          design documents and diagrams
```

## Adding a new service

1. Create `services/<name>/` with a Maven `pom.xml` and `src/`.
2. Copy `templates/Dockerfile.service` to `services/<name>/Dockerfile`.
3. Add the service block (see the commented example at the bottom of `docker-compose.yml`).
4. Expose `/actuator/health` and `/actuator/prometheus`.
5. Read secrets from environment variables only. Never hard-code them.

## Rules

- Local passwords in `docker-compose.yml` are dev-only defaults. To override them create a `.env` file (it is git-ignored). Never commit `.env` or any real secret.
- Every PR needs one review and a green CI run.
- Minimum 70% test coverage per service.
