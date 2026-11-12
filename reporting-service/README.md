# SwiftDrop - Reporting Service (Member 10)

The **Reporting Service** is an event-fed read model microservice in the SwiftDrop parcel delivery platform. It listens to asynchronous events over RabbitMQ, updates its own dedicated read model database (`reporting_db`), and exposes read-only reporting REST endpoints for Administrators, Dispatchers, and Merchants.

---

## 🛠 Technology Stack

- **Java**: 21
- **Framework**: Spring Boot 3.3.5
- **Persistence**: Spring Data JPA, Hibernate, PostgreSQL
- **Database Migrations**: Flyway
- **Messaging**: Spring AMQP (RabbitMQ)
- **Security**: Spring Security, OAuth2 Resource Server (JWT)
- **Observability**: Spring Boot Actuator, Micrometer Prometheus
- **Testing**: JUnit 5, Mockito, Testcontainers (PostgreSQL, RabbitMQ)
- **Coverage**: JaCoCo (minimum 70% line coverage enforcement)
- **Containerization**: Docker, Docker Compose

---

## 🚀 Quick Start (Local Docker Compose)

To start the Reporting Service along with PostgreSQL and RabbitMQ:

```bash
docker compose up -d
```

- **Reporting Service API**: `http://localhost:8084`
- **RabbitMQ Management UI**: `http://localhost:15672` (Username: `guest`, Password: `guest`)
- **PostgreSQL Database**: `localhost:5434` (DB: `reporting_db`, User: `postgres`, Password: `postgres`)
- **Actuator Health**: `http://localhost:8084/actuator/health`
- **Prometheus Metrics**: `http://localhost:8084/actuator/prometheus`

---

## ⚙️ Configuration & Environment Variables

| Variable | Description | Default Value |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/reporting_db` |
| `DB_USERNAME` | PostgreSQL database username | `postgres` |
| `DB_PASSWORD` | PostgreSQL database password | `postgres` |
| `RABBITMQ_HOST` | RabbitMQ broker host | `localhost` |
| `RABBITMQ_PORT` | RabbitMQ broker port | `5672` |
| `REPORTING_TIMEZONE` | Timezone for daily counter aggregation | `Asia/Colombo` |
| `SERVER_PORT` | HTTP server port | `8084` |
| `JWT_ISSUER_URI` | JWT issuer URI for token validation | `http://localhost:8081` |

---

## 📡 REST API Summary

All endpoints are read-only under `/api/v1` and protected by role-based authorization:

| Method | Endpoint | Required Role | Description |
|---|---|---|---|
| `GET` | `/api/v1/admin/reports/summary?from&to` | `ADMIN`, `DISPATCHER` | Created, delivered, failed counts, attempt & outcome rates, active couriers, lastUpdatedAt |
| `GET` | `/api/v1/admin/reports/deliveries-per-day?from&to&zoneId` | `ADMIN`, `DISPATCHER` | Trend points per day with optional zone filter |
| `GET` | `/api/v1/admin/reports/success-rate?from&to` | `ADMIN`, `DISPATCHER` | Attempt rate and final-outcome rate over date range |
| `GET` | `/api/v1/admin/reports/courier-performance?from&to&sort` | `ADMIN`, `DISPATCHER` | Courier metrics, assigned/delivered counts, and average delivery minutes |
| `GET` | `/api/v1/admin/reports/zones?from&to` | `ADMIN`, `DISPATCHER` | Aggregated delivery metrics and total fees by zone |
| `GET` | `/api/v1/merchant/reports/orders?from&to` | `MERCHANT` | Caller's merchant metrics (extracted securely from JWT token claims) |

> Append `&format=csv` to any report endpoint to receive downloadable CSV data protected against spreadsheet formula injection (`=`, `+`, `-`, `@` escaped).

---

## 🧪 Running Tests & Coverage

Run the full unit and integration test suite:

```bash
./mvnw clean verify
```

The JaCoCo line coverage report is automatically generated at `target/site/jacoco/index.html`.
A hard threshold of **70% line coverage** is enforced during `verify`.

---

## 🔄 CI/CD Pipeline

A GitHub Actions workflow is located at `.github/workflows/ci.yml`. On every push and pull request to `main` and `master`, it:
1. Sets up JDK 21.
2. Executes `./mvnw clean verify -B`.
3. Runs the T1–T9 test suite with Testcontainers (PostgreSQL & RabbitMQ).
4. Verifies the JaCoCo coverage threshold.
5. Uploads the JaCoCo HTML report as an artifact (`jacoco-coverage-report`).
