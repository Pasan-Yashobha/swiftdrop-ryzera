# Identity Service

Spring Boot service of the SwiftDrop platform (Member 2). It owns **identity & authentication**: user registration,
credential verification, JWT access token issuance (RS256 with published JWKS), refresh token rotation (RTR) with reuse
detection and session family tracking, account lockout defense against brute-force attacks, profile management, and RBAC
enforcement across all platform roles (`CUSTOMER`, `MERCHANT`, `COURIER`, `ADMIN`, `DISPATCHER`).

| Item | Value |
|---|---|
| Stack | Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, Spring Security, Nimbus JOSE JWT |
| Database | PostgreSQL `identity_db`, role `identity_svc`, schema in `src/main/resources/db/migration/V1__init.sql` |
| Ports | `8080` inside container, `8081` on host (docker-compose) |
| Health and metrics | `/actuator/health` and `/actuator/prometheus` |
| JWKS endpoint | `/.well-known/jwks.json` and `/api/v1/auth/.well-known/jwks.json` |
