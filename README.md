# SwiftDrop: Parcel Delivery & Courier Management System

Stack: Angular, Java Spring Boot, PostgreSQL (AWS RDS), RabbitMQ, Docker, Kubernetes, AWS CDK or OpenTofu, Actuator + Prometheus + Grafana.
Local development uses Docker Compose (Postgres runs in a container); the cloud deployment uses RDS.

Enterprise application development group project (10 members).

## Quick start (local platform stack)

```bash
cp .env.example .env          # then edit the passwords
docker compose up -d          # Postgres, RabbitMQ, Redis, MinIO, Mailpit
docker compose ps             # everything should become "healthy"
```

Optional extras:

```bash
docker compose --profile observability up -d   # Prometheus + Grafana
```

## Local URLs

| Tool | URL | Notes |
|---|---|---|
| RabbitMQ UI | http://localhost:15672 | user/pass from `.env` |
| MinIO console | http://localhost:9001 | buckets `proof-photos`, `receipts` are auto-created |
| Mailpit inbox | http://localhost:8025 | services send SMTP to `mailpit:1025` |
| Prometheus | http://localhost:9090 | `observability` profile |
| Grafana | http://localhost:3001 | `observability` profile |
| PostgreSQL | localhost:5432 | one DB per service: `<service>_db`, user `<service>_svc` |

## Repository layout

```
services/      one folder per microservice (Spring Boot, Maven)
frontend/      Angular workspace (customer app, merchant portal, courier PWA, admin console, shared library)
infra/         local Postgres init, Prometheus, Grafana config, plus cloud/ (AWS CDK or OpenTofu) and k8s/ (manifests)
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

- Never commit `.env` or any secret.
- Every PR needs one review and a green CI run.
- Minimum 70% test coverage per service.
